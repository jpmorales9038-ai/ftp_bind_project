#!/system/bin/sh
MODDIR=${0%/*}

# Espera a que el sistema esté listo
while [ "$(getprop sys.boot_completed)" != "1" ]; do
    sleep 1
done

# Instalar/actualizar la app acá (no en customize.sh): recién ahora
# system_server/installd están garantizado arriba y accesibles por
# binder, así "pm install" no falla con el genérico "Failure calling
# service package: Failed transaction" que se veía al intentarlo en
# tiempo de flasheo. Solo se reinstala cuando cambia el versionCode del
# module.prop empaquetado, para no repetir el install en cada boot.
APK_SRC="$MODDIR/app.apk"
APK_TMP="/data/local/tmp/rclone_ftp_bind.apk"
INSTALL_LOG="$MODDIR/install.log"
VERSION_MARKER="$MODDIR/.installed_version"
CURRENT_VERSION="$(grep '^versionCode=' "$MODDIR/module.prop" | cut -d= -f2)"

if [ -f "$APK_SRC" ] && [ "$(cat "$VERSION_MARKER" 2>/dev/null)" != "$CURRENT_VERSION" ]; then
    cp "$APK_SRC" "$APK_TMP"
    # installd lee el APK con otro UID que el "cp" como root: sin esto,
    # falla con el mismo error genérico por permisos, no por el binder.
    chmod 644 "$APK_TMP"
    if pm install -r "$APK_TMP" >> "$INSTALL_LOG" 2>&1; then
        echo "$CURRENT_VERSION" > "$VERSION_MARKER"
        echo "$(date): App instalada/actualizada a v$CURRENT_VERSION" >> "$INSTALL_LOG"
    else
        # Si de verdad fue un choque de firma (instalación previa firmada
        # distinto), un uninstall + install limpio lo resuelve. La config
        # del FTP no se pierde: vive en $MODDIR/config, no en los datos
        # de la app.
        pm uninstall com.rclonebind.app >> "$INSTALL_LOG" 2>&1
        if pm install -r "$APK_TMP" >> "$INSTALL_LOG" 2>&1; then
            echo "$CURRENT_VERSION" > "$VERSION_MARKER"
            echo "$(date): App reinstalada (limpio) a v$CURRENT_VERSION" >> "$INSTALL_LOG"
        else
            echo "$(date): Fallo al instalar la app automáticamente, revisa $INSTALL_LOG" >> "$INSTALL_LOG"
        fi
    fi
    rm -f "$APK_TMP"
fi

AUTOSTART_FLAG="$MODDIR/config/autostart"

if [ -f "$AUTOSTART_FLAG" ] && [ "$(cat "$AUTOSTART_FLAG")" = "1" ]; then
    sh "$MODDIR/scripts/mount.sh"
fi
