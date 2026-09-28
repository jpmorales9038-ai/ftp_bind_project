#!/system/bin/sh
SELF="$(readlink -f "$0")"
MODDIR=$(dirname "$(dirname "$SELF")")

# Si esto corre desde el "su" de la app (RootShell -> libsu) o desde
# ciertos service.sh, el proceso puede quedar en un mount namespace
# PRIVADO en vez del namespace global (el de init/PID 1). El mount y el
# bind se hacen igual y rclone loguea éxito ("Montado correctamente"),
# pero el bind solo existe en ese namespace aislado — ningún otro
# proceso del sistema (explorador de archivos incluido) lo ve. Por eso
# "dice que monta pero no aparecen los archivos". Forzamos re-ejecutar
# este script ya adentro del namespace de PID 1 para que el mount se
# propague a todo el sistema.
if [ "$(readlink /proc/self/ns/mnt 2>/dev/null)" != "$(readlink /proc/1/ns/mnt 2>/dev/null)" ]; then
    exec nsenter -t 1 -m -- sh "$SELF" "$@"
fi

RCLONE_BIN="$MODDIR/bin/rclone"
RCLONE_CONF="$MODDIR/config/rclone.conf"
LOG_FILE="$MODDIR/mount.log"
STATUS_FILE="$MODDIR/status.json"
CACHE_DIR="$MODDIR/cache"

# Punto donde rclone monta realmente el FTP
RCLONE_MOUNTPOINT="/data/local/tmp/rclone_ftp"
# Ruta final visible en el almacenamiento interno (bind). Configurable desde
# la app (config/target_path); sin ese archivo se usa la de siempre.
TARGET_PATH="$(cat "$MODDIR/config/target_path" 2>/dev/null)"
[ -z "$TARGET_PATH" ] && TARGET_PATH="/sdcard/FTP"

# Al correr como root vía su/servicio, $HOME suele venir vacío o en "/", y
# rclone intenta entonces crear su vfs cache en "/.cache" — que cae en la
# partición de sistema, de solo lectura ("mkdir /.cache: read-only file
# system"). Fijamos HOME a un directorio propio y escribible del módulo, y
# además pasamos --cache-dir explícito para no depender de HOME en absoluto.
export HOME="$MODDIR"
mkdir -p "$CACHE_DIR"

# Android no trae fusermount3 (rclone lo necesita para montar FUSE incluso
# corriendo como root: "fusermount3: executable file not found in $PATH").
# Se agrega $MODDIR/bin (donde va el binario que empaqueta el módulo) al
# PATH para que rclone lo encuentre.
export PATH="$MODDIR/bin:$PATH"

if [ ! -f "$RCLONE_CONF" ]; then
    echo "$(date): No hay rclone.conf, configura el FTP desde la app" >> "$LOG_FILE"
    exit 1
fi

# Servidor seleccionado en la app (config/active). Sin ese archivo se usa
# "remote", el nombre que usaban las versiones con un solo servidor.
ACTIVE="$(cat "$MODDIR/config/active" 2>/dev/null)"
[ -z "$ACTIVE" ] && ACTIVE="remote"
if ! grep -qxF "[$ACTIVE]" "$RCLONE_CONF"; then
    echo "$(date): No existe el servidor '$ACTIVE' en rclone.conf" >> "$LOG_FILE"
    exit 1
fi

mkdir -p "$RCLONE_MOUNTPOINT"
# Estado actual: el montaje FUSE de rclone y el bind sobre /sdcard/FTP son
# dos cosas distintas. Si un intento anterior dejó el FUSE pero no el bind,
# solo hay que completar el bind.
is_fuse_mounted() { grep -q " $RCLONE_MOUNTPOINT " /proc/mounts; }

# OJO: /proc/mounts muestra la ruta REAL ya resuelta (/sdcard es un symlink
# a /storage/emulated/0 o similar), nunca "/sdcard/FTP". Buscar ese texto
# ahí daba siempre "no está bound" aunque el bind sí se hubiera hecho, y el
# script reportaba "Falló el bind" en cada intento (apilando un bind nuevo
# encima del anterior cada vez). Se compara el id de dispositivo: un bind
# de la carpeta FUSE tiene exactamente el mismo st_dev que el original.
is_bound_at() {
    a="$(stat -c %d "$1" 2>/dev/null)"
    b="$(stat -c %d "$RCLONE_MOUNTPOINT" 2>/dev/null)"
    [ -n "$a" ] && [ "$a" = "$b" ]
}

try_bind() {
    # $1 = ruta destino a probar
    mkdir -p "$1" 2>>"$LOG_FILE"
    is_bound_at "$1" && return 0
    err="$(mount --bind "$RCLONE_MOUNTPOINT" "$1" 2>&1)" || \
        echo "$(date): mount --bind hacia $1 falló: $err" >> "$LOG_FILE"
    is_bound_at "$1"
}

# Ruta de respaldo: el almacenamiento real debajo de /sdcard. Dentro del
# namespace de PID 1 a veces /sdcard no apunta a la vista de usuario.
fallback_path() {
    case "$TARGET_PATH" in
        /sdcard/*) echo "/data/media/0/${TARGET_PATH#/sdcard/}" ;;
        /storage/emulated/0/*) echo "/data/media/0/${TARGET_PATH#/storage/emulated/0/}" ;;
        /storage/self/primary/*) echo "/data/media/0/${TARGET_PATH#/storage/self/primary/}" ;;
    esac
}

do_bind() {
    USED="$TARGET_PATH"
    if ! try_bind "$TARGET_PATH"; then
        FB="$(fallback_path)"
        if [ -n "$FB" ] && try_bind "$FB"; then
            USED="$FB"
        else
            echo '{"mounted":false}' > "$STATUS_FILE"
            echo "$(date): Falló el bind hacia $TARGET_PATH" >> "$LOG_FILE"
            exit 1
        fi
    fi
    # Se guarda la ruta REAL usada: unmount.sh debe apuntar a esta aunque
    # el usuario cambie la ruta desde la app mientras sigue montado.
    echo "{\"mounted\":true,\"remote\":\"$ACTIVE\",\"target\":\"$USED\"}" > "$STATUS_FILE"
    echo "$(date): '$ACTIVE' montado correctamente en $USED" >> "$LOG_FILE"
    exit 0
}

if is_fuse_mounted; then
    do_bind
fi

"$RCLONE_BIN" mount "$ACTIVE:" "$RCLONE_MOUNTPOINT" \
    --config "$RCLONE_CONF" \
    --cache-dir "$CACHE_DIR" \
    --allow-other \
    --vfs-cache-mode writes \
    --daemon \
    --log-file "$LOG_FILE" \
    --log-level INFO

sleep 2

if is_fuse_mounted; then
    do_bind
else
    echo '{"mounted":false}' > "$STATUS_FILE"
    echo "$(date): Fallo al montar rclone" >> "$LOG_FILE"
    exit 1
fi
