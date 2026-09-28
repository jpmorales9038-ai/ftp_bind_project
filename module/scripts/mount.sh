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
mkdir -p "$TARGET_PATH"

# Estado actual: el montaje FUSE de rclone y el bind sobre /sdcard/FTP son
# dos cosas distintas. Si un intento anterior dejó el FUSE pero no el bind,
# solo hay que completar el bind (antes salía con "Ya estaba montado" sin
# hacerlo, y el bind nunca aparecía).
is_fuse_mounted() { grep -q " $RCLONE_MOUNTPOINT " /proc/mounts; }
is_bound() { grep -q " $TARGET_PATH " /proc/mounts; }

do_bind() {
    is_bound || mount --bind "$RCLONE_MOUNTPOINT" "$TARGET_PATH"
    if is_bound; then
        # Se guarda el TARGET_PATH real usado (no solo el de config): si el
        # usuario cambia la ruta desde la app mientras esto sigue montado en
        # la anterior, unmount.sh debe seguir apuntando a esta, no a la nueva.
        echo "{\"mounted\":true,\"remote\":\"$ACTIVE\",\"target\":\"$TARGET_PATH\"}" > "$STATUS_FILE"
        echo "$(date): '$ACTIVE' montado correctamente en $TARGET_PATH" >> "$LOG_FILE"
        exit 0
    fi
    echo '{"mounted":false}' > "$STATUS_FILE"
    echo "$(date): Falló el bind hacia $TARGET_PATH" >> "$LOG_FILE"
    exit 1
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
