#!/system/bin/sh
SELF="$(readlink -f "$0")"
MODDIR=$(dirname "$(dirname "$SELF")")
. "$MODDIR/scripts/common.sh"
enter_global_namespace "$@"
if ! is_mount_at /data/local/tmp/rclone_ftp; then
    printf '{"mounted":false}\n'
else
    cat "$MODDIR/status.json" 2>/dev/null || printf '{"mounted":false}\n'
fi
