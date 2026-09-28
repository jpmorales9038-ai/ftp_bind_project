#!/system/bin/sh
MODDIR=${0%/*}

while [ "$(getprop sys.boot_completed)" != "1" ]; do
    sleep 1
done

# Si customize.sh no pudo instalar al flashear (dejó .needs_manual_install),
# se reintenta ahora que el sistema ya arrancó; si sigue fallando, se abre el
# instalador del sistema y basta un toque en "Instalar".
NEEDS_MANUAL="$MODDIR/.needs_manual_install"
APK_SRC="$MODDIR/app.apk"
APK_TMP="/data/local/tmp/rclone_ftp_bind.apk"
INSTALL_LOG="$MODDIR/install.log"

if [ -f "$NEEDS_MANUAL" ] && [ -f "$APK_SRC" ]; then
    . "$MODDIR/scripts/install_app.sh"
    cp "$APK_SRC" "$APK_TMP"
    chmod 644 "$APK_TMP"
    if ! install_app "$APK_TMP" "$INSTALL_LOG"; then
        am start -a android.intent.action.VIEW \
            -d "file://$APK_TMP" \
            -t application/vnd.android.package-archive \
            -f 0x10000000 >> "$INSTALL_LOG" 2>&1
    fi
    rm -f "$NEEDS_MANUAL"
fi

AUTOSTART_FLAG="$MODDIR/config/autostart"

if [ -f "$AUTOSTART_FLAG" ] && [ "$(cat "$AUTOSTART_FLAG")" = "1" ]; then
    sh "$MODDIR/scripts/mount.sh"
fi
