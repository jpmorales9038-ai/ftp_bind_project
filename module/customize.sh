SKIPUNZIP=0

ui_print "- Instalando RClone FTP Bind"

mkdir -p "$MODPATH/config"
mkdir -p "$MODPATH/scripts"

chmod 755 "$MODPATH/bin/rclone"
chmod 755 "$MODPATH/scripts/"*.sh
chmod 755 "$MODPATH/service.sh"
chmod 755 "$MODPATH/post-fs-data.sh"

# Config por defecto: sin autostart hasta que el usuario lo active desde la app
[ -f "$MODPATH/config/autostart" ] || echo "0" > "$MODPATH/config/autostart"

# Instalar / actualizar la app con pm install -r (sobrescribe conservando
# datos). No condicionamos por $BOOTMODE: KernelSU no siempre expone esa
# variable como Magisk, y filtrar por ella dejaba el install sin correr
# aunque el sistema sí estuviera arrancado. Si pm falla igual (por ejemplo
# flasheado real desde recovery), queda logueado y se avisa para instalar
# a mano.
APK_SRC="$MODPATH/app.apk"
APK_TMP="/data/local/tmp/rclone_ftp_bind.apk"

if [ -f "$APK_SRC" ]; then
    ui_print "- Instalando/actualizando la app (pm install -r)"
    cp "$APK_SRC" "$APK_TMP"
    if pm install -r "$APK_TMP" >> "$MODPATH/install.log" 2>&1; then
        ui_print "- App instalada/actualizada correctamente"
    else
        ui_print "- No se pudo instalar la app automáticamente"
        ui_print "  revisa $MODPATH/install.log o instálala manualmente: $APK_SRC"
    fi
    rm -f "$APK_TMP"
fi

ui_print "- Configura el remoto FTP desde la app antes de montar"
