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
# Se intenta acá, como pidió el usuario, aunque el binder hacia
# system_server puede no estar disponible en este punto en algunos
# dispositivos. Si falla (silenciosamente o por el mismo bloqueo SELinux
# del dominio "su" que afecta a pm/cmd package en este dispositivo), se
# deja una marca para que service.sh, ya con el sistema arrancado,
# abra el instalador del sistema y el usuario solo tenga que tocar
# "Instalar" una vez — eso lo hace un proceso privilegiado del sistema,
# no el shell confinado, así que funciona pase lo que pase con SELinux.
APK_SRC="$MODPATH/app.apk"
APK_TMP="/data/local/tmp/rclone_ftp_bind.apk"
INSTALL_LOG="$MODPATH/install.log"
VERSION_MARKER="$MODPATH/.installed_version"
CURRENT_VERSION="$(grep '^versionCode=' "$MODPATH/module.prop" | cut -d= -f2)"

if [ -f "$APK_SRC" ]; then
    cp "$APK_SRC" "$APK_TMP"
    chmod 644 "$APK_TMP"

    INSTALLED=0
    if cmd package install -r "$APK_TMP" >> "$INSTALL_LOG" 2>&1; then
        INSTALLED=1
    elif pm install -r "$APK_TMP" >> "$INSTALL_LOG" 2>&1; then
        INSTALLED=1
    else
        cmd package uninstall com.rclonebind.app >> "$INSTALL_LOG" 2>&1
        pm uninstall com.rclonebind.app >> "$INSTALL_LOG" 2>&1
        if cmd package install -r "$APK_TMP" >> "$INSTALL_LOG" 2>&1 || pm install -r "$APK_TMP" >> "$INSTALL_LOG" 2>&1; then
            INSTALLED=1
        fi
    fi

    if [ "$INSTALLED" = "1" ]; then
        echo "$CURRENT_VERSION" > "$VERSION_MARKER"
        ui_print "- App instalada correctamente"
    else
        ui_print "- No se pudo instalar en modo silencioso (bloqueo del sistema)"
        ui_print "- Al reiniciar se abrirá el instalador: toca \"Instalar\" una vez"
        touch "$MODPATH/.needs_manual_install"
    fi
    rm -f "$APK_TMP"
fi

ui_print "- Configura el remoto FTP desde la app antes de montar"
