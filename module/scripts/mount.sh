#!/system/bin/sh
MODDIR=$(dirname "$(dirname "$(readlink -f "$0")")")

RCLONE_BIN="$MODDIR/bin/rclone"
RCLONE_CONF="$MODDIR/config/rclone.conf"
LOG_FILE="$MODDIR/mount.log"
STATUS_FILE="$MODDIR/status.json"

# Punto donde rclone monta realmente el FTP
RCLONE_MOUNTPOINT="/data/local/tmp/rclone_ftp"
# Ruta final visible en el almacenamiento interno (bind)
TARGET_PATH="/sdcard/FTP"

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
