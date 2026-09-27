#!/system/bin/sh
MODDIR=${0%/*}

# Espera a que el sistema esté listo
while [ "$(getprop sys.boot_completed)" != "1" ]; do
    sleep 1
done

AUTOSTART_FLAG="$MODDIR/config/autostart"

if [ -f "$AUTOSTART_FLAG" ] && [ "$(cat "$AUTOSTART_FLAG")" = "1" ]; then
    sh "$MODDIR/scripts/mount.sh"
fi
