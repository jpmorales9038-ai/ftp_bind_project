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

# La instalación del APK NO se hace acá. customize.sh corre en tiempo de
# flasheo del módulo, y en ese momento el binder hacia system_server (el
# que atiende "pm install") puede no estar disponible o quedar bloqueado
# por SELinux según el dispositivo/manager — eso da el error genérico
# "Failure calling service package: Failed transaction (2147483646)" sin
# que tenga que ver con un choque de firma. pm install SÍ funciona de
# forma confiable una vez que el sistema terminó de arrancar, así que la
# instalación/actualización se hace en service.sh (ver ahí).
ui_print "- La app se instalará/actualizará sola al terminar de arrancar"
ui_print "- Configura el remoto FTP desde la app antes de montar"
