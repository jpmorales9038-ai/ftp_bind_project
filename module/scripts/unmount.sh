#!/system/bin/sh
MODDIR=$(dirname "$(dirname "$(readlink -f "$0")")")

LOG_FILE="$MODDIR/mount.log"
STATUS_FILE="$MODDIR/status.json"

RCLONE_MOUNTPOINT="/data/local/tmp/rclone_ftp"
TARGET_PATH="/sdcard/FTP"

umount -l "$TARGET_PATH" 2>>"$LOG_FILE"
umount -l "$RCLONE_MOUNTPOINT" 2>>"$LOG_FILE" || "$MODDIR/bin/fusermount3" -u "$RCLONE_MOUNTPOINT" 2>>"$LOG_FILE"

# Por si el mount corre como proceso en background
pkill -f "rclone mount remote" 2>/dev/null

echo '{"mounted":false}' > "$STATUS_FILE"
echo "$(date): Desmontado" >> "$LOG_FILE"
