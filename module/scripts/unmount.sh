#!/system/bin/sh
SELF="$(readlink -f "$0")"
MODDIR=$(dirname "$(dirname "$SELF")")

# Mismo motivo que en mount.sh: forzar el namespace global de PID 1 para
# que el umount le pegue al mount real, no a una vista privada del proceso.
if [ "$(readlink /proc/self/ns/mnt 2>/dev/null)" != "$(readlink /proc/1/ns/mnt 2>/dev/null)" ]; then
    exec nsenter -t 1 -m -- sh "$SELF" "$@"
fi

LOG_FILE="$MODDIR/mount.log"
STATUS_FILE="$MODDIR/status.json"

RCLONE_MOUNTPOINT="/data/local/tmp/rclone_ftp"
TARGET_PATH="/sdcard/FTP"

umount -l "$TARGET_PATH" 2>>"$LOG_FILE"
umount -l "$RCLONE_MOUNTPOINT" 2>>"$LOG_FILE" || "$MODDIR/bin/fusermount3" -u "$RCLONE_MOUNTPOINT" 2>>"$LOG_FILE"

# Por si el mount corre como proceso en background
pkill -f "$MODDIR/bin/rclone mount" 2>/dev/null

echo '{"mounted":false}' > "$STATUS_FILE"
echo "$(date): Desmontado" >> "$LOG_FILE"
