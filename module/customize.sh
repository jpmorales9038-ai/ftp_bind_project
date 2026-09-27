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

# Instalar / actualizar la app. $BOOTMODE = true cuando el módulo se flashea
# desde el Manager de KernelSU con el sistema ya arrancado (system_server
# disponible para "pm install"); si se flashea desde recovery no hay forma
# de invocar pm, así que en ese caso se avisa para instalarla a mano.
APK_SRC="$MODPATH/app.apk"
APK_TMP="/data/local/tmp/rclone_ftp_bind.apk"

if [ -f "$APK_SRC" ]; then
    if [ "$BOOTMODE" = "true" ]; then
        ui_print "- Instalando/actualizando la app (pm install -r)"
        cp "$APK_SRC" "$APK_TMP"
        if pm install -r "$APK_TMP" >> "$MODPATH/install.log" 2>&1; then
            ui_print "- App instalada/actualizada correctamente"
        else
            ui_print "- No se pudo instalar la app automáticamente, revisa $MODPATH/install.log"
        fi
        rm -f "$APK_TMP"
    else
        ui_print "- Flasheado desde recovery: instala $APK_SRC manualmente"
    fi
fi

ui_print "- Configura el remoto FTP desde la app antes de montar"
