#!/system/bin/sh
SELF="$(readlink -f "$0")"
MODDIR=$(dirname "$(dirname "$SELF")")
. "$MODDIR/scripts/common.sh"
enter_global_namespace "$@"
acquire_operation_lock
. "$MODDIR/scripts/env.sh"
LOG_FILE="$MODDIR/mount.log"
STATUS_FILE="$MODDIR/status.json"
RCLONE_MOUNTPOINT="/data/local/tmp/rclone_ftp"
TARGET_PATH="$(sed -n 's/.*"target":"\([^"]*\)".*/\1/p' "$STATUS_FILE" 2>/dev/null)"
if is_mount_at "$RCLONE_MOUNTPOINT"; then
    valid_target "$TARGET_PATH" || { echo "ERROR: destino desconocido; no se desmonta a ciegas"; exit 1; }
    setup_rc || exit 1
    # No force mode: open writers or failed uploads leave the mount intact.
    n=0
    while :; do
        stats=$(rc_call vfs/stats 2>>"$LOG_FILE") || { echo "ERROR: no se puede comprobar la cola VFS; montaje conservado"; exit 1; }
        pending=$(printf '%s\n' "$stats" | awk '
            /"(uploadsQueued|uploadsInProgress|erroredFiles|inUse)"[ \t]*:/ {
                v=$0; sub(/.*:[ \t]*/,"",v); sub(/,.*/,"",v)
                if (v !~ /^[0-9]+[ \t]*$/) bad=1
                sum+=v; count++
            }
            END {if (bad || count!=4) print "unknown"; else print sum+0}')
        [ "$pending" = 0 ] && break
        [ "$pending" = unknown ] && { echo "ERROR: respuesta VFS incompatible; montaje conservado"; exit 1; }
        [ "$n" -ge 30 ] && { echo "ERROR: quedan archivos abiertos/subidas pendientes; cierra las apps y reintenta"; exit 1; }
        sleep 2; n=$((n + 1))
    done
    # Stop only this module's helpers, and their children.
    for helper in watch.sh preload.sh perf_test.sh; do
        for pid in $(pgrep -f "$MODDIR/scripts/$helper" 2>/dev/null); do
            stop_children "$pid"; kill -TERM "$pid" 2>/dev/null
        done
    done
    a=$(stat -c %d "$TARGET_PATH" 2>/dev/null)
    b=$(stat -c %d "$RCLONE_MOUNTPOINT" 2>/dev/null)
    if [ -n "$a" ] && [ "$a" = "$b" ]; then
        umount "$TARGET_PATH" 2>>"$LOG_FILE" || { sh "$MODDIR/scripts/watch.sh" </dev/null >/dev/null 2>&1 &
            echo "ERROR: bind ocupado; montaje conservado"; exit 1; }
    fi
    umount "$RCLONE_MOUNTPOINT" 2>>"$LOG_FILE" || {
        mount --bind "$RCLONE_MOUNTPOINT" "$TARGET_PATH" 2>>"$LOG_FILE"
        sh "$MODDIR/scripts/watch.sh" </dev/null >/dev/null 2>&1 &
        echo "ERROR: FUSE ocupado; desmontaje rechazado"; exit 1;
    }
fi
is_mount_at "$RCLONE_MOUNTPOINT" && { echo "ERROR: FUSE aun activo"; exit 1; }
# Do not kill rclone by command pattern; a clean unmount ends its mount loop.
if is_mount_at "$MODDIR/cache_ram"; then
    umount "$MODDIR/cache_ram" 2>>"$LOG_FILE" || { echo "ERROR: tmpfs ocupado"; exit 1; }
fi
atomic_unmounted
rm -f "$MODDIR/watch.pid"
echo "$(date): Desmontado sin forzar; cola VFS vacia" >> "$LOG_FILE"
