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

ui_print "- Configura el remoto FTP desde la app antes de montar"
