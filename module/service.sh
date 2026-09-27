#!/system/bin/sh
MODDIR=${0%/*}

while [ "$(getprop sys.boot_completed)" != "1" ]; do
    sleep 1
done

# Si customize.sh no pudo instalar en silencio al flashear (dejó la marca
# .needs_manual_install), se abre el instalador del sistema con el APK ya
# preparado. La instalación la termina de hacer el propio instalador
# privilegiado del sistema tras un toque del usuario en "Instalar" — no
# depende de pm/cmd package desde el shell root, así que no le afecta el
# bloqueo SELinux del dominio "su" que se vio en pruebas reales.
NEEDS_MANUAL="$MODDIR/.needs_manual_install"
APK_SRC="$MODDIR/app.apk"
APK_TMP="/data/local/tmp/rclone_ftp_bind.apk"
INSTALL_LOG="$MODDIR/install.log"

if [ -f "$NEEDS_MANUAL" ] && [ -f "$APK_SRC" ]; then
    cp "$APK_SRC" "$APK_TMP"
    chmod 644 "$APK_TMP"
    am start -a android.intent.action.VIEW \
        -d "file://$APK_TMP" \
        -t application/vnd.android.package-archive \
        -f 0x10000000 >> "$INSTALL_LOG" 2>&1
    rm -f "$NEEDS_MANUAL"
fi

AUTOSTART_FLAG="$MODDIR/config/autostart"

if [ -f "$AUTOSTART_FLAG" ] && [ "$(cat "$AUTOSTART_FLAG")" = "1" ]; then
    sh "$MODDIR/scripts/mount.sh"
fi
