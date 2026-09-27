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
# Ruta final visible en el almacenamiento interno (bind)
TARGET_PATH="/sdcard/FTP"

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

mkdir -p "$RCLONE_MOUNTPOINT"
mkdir -p "$TARGET_PATH"

# Ya montado, no hacer nada
if mount | grep -q "$RCLONE_MOUNTPOINT"; then
    echo "$(date): Ya estaba montado" >> "$LOG_FILE"
    exit 0
fi

"$RCLONE_BIN" mount remote: "$RCLONE_MOUNTPOINT" \
    --config "$RCLONE_CONF" \
    --cache-dir "$CACHE_DIR" \
    --allow-other \
    --vfs-cache-mode writes \
    --daemon \
    --log-file "$LOG_FILE" \
    --log-level INFO

sleep 2

if mount | grep -q "$RCLONE_MOUNTPOINT"; then
    mount --bind "$RCLONE_MOUNTPOINT" "$TARGET_PATH"
    echo '{"mounted":true}' > "$STATUS_FILE"
    echo "$(date): Montado correctamente en $TARGET_PATH" >> "$LOG_FILE"
else
    echo '{"mounted":false}' > "$STATUS_FILE"
    echo "$(date): Fallo al montar rclone" >> "$LOG_FILE"
    exit 1
fi
