SKIPUNZIP=0

ui_print "- Instalando RClone FTP Bind"

mkdir -p "$MODPATH/config"
mkdir -p "$MODPATH/scripts"

chmod 755 "$MODPATH/bin/rclone"
chmod 755 "$MODPATH/bin/fusermount3" 2>/dev/null
chmod 755 "$MODPATH/scripts/"*.sh
chmod 755 "$MODPATH/service.sh"
chmod 755 "$MODPATH/post-fs-data.sh"

# Config por defecto: sin autostart hasta que el usuario lo active desde la app
[ -f "$MODPATH/config/autostart" ] || echo "0" > "$MODPATH/config/autostart"

# --- Instalación de la app en tiempo de flasheo ---
# Si falla en silencio (p. ej. el binder a system_server no está listo o
# SELinux lo bloquea), se deja una marca para que service.sh reintente ya con
# el sistema arrancado y, como último recurso, abra el instalador del sistema.
APK_SRC="$MODPATH/app.apk"
APK_TMP="/data/local/tmp/rclone_ftp_bind.apk"
INSTALL_LOG="$MODPATH/install.log"

if [ -f "$APK_SRC" ]; then
    . "$MODPATH/scripts/install_app.sh"
    cp "$APK_SRC" "$APK_TMP"
    chmod 644 "$APK_TMP"

    if install_app "$APK_TMP" "$INSTALL_LOG"; then
        ui_print "- App instalada correctamente"
    else
        ui_print "- No se pudo instalar la app al flashear:"
        ui_print "  $(echo "$LAST_ERR" | tail -n 1)"
        ui_print "- Se reintentará al reiniciar (log: $INSTALL_LOG)"
        touch "$MODPATH/.needs_manual_install"
    fi
    rm -f "$APK_TMP"
else
    ui_print "- AVISO: el zip no trae app.apk"
fi

ui_print "- Configura el remoto FTP desde la app antes de montar"
