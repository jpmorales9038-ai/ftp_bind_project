# Entorno común de los scripts que ejecutan rclone (mount.sh, drive_auth.sh,
# check_remote.sh). Se carga con:  . "$MODDIR/scripts/env.sh"
# Requiere que $MODDIR ya esté definido por quien lo carga.

# Al correr como root vía su/servicio, $HOME suele venir vacío o en "/", y
# rclone intenta entonces crear su config/cache en "/.cache" — que cae en la
# partición de sistema, de solo lectura. Se fija HOME a un directorio propio
# y escribible del módulo.
export HOME="$MODDIR"

# Android no trae fusermount3 (rclone lo necesita para montar FUSE incluso
# corriendo como root). Se agrega $MODDIR/bin al PATH para que lo encuentre.
export PATH="$MODDIR/bin:$PATH"

# rclone es un binario Go estático "linux": no conoce el almacén de
# certificados de Android y sin esto toda conexión HTTPS (Google Drive) falla
# con "x509: certificate signed by unknown authority". Go acepta una lista de
# directorios separados por ":" en SSL_CERT_DIR. Se usa el almacén del APEX
# conscrypt (Android 14+, se actualiza por Play) junto al clásico del sistema.
CERT_DIRS=""
for d in /apex/com.android.conscrypt/cacerts /system/etc/security/cacerts; do
    [ -d "$d" ] && CERT_DIRS="${CERT_DIRS:+$CERT_DIRS:}$d"
done
[ -n "$CERT_DIRS" ] && export SSL_CERT_DIR="$CERT_DIRS"
unset CERT_DIRS

# Never overlay all of /system/etc. Only bind the resolver file when an
# existing mount target is available. If missing, the module's system file
# requires working systemless mounting (KernelSU metamodule where applicable).
if ! grep -qs '^nameserver' /system/etc/resolv.conf; then
    if [ -f /system/etc/resolv.conf ] && [ -f "$MODDIR/system/etc/resolv.conf" ]; then
        mount --bind "$MODDIR/system/etc/resolv.conf" /system/etc/resolv.conf 2>/dev/null || :
    fi
    if ! grep -qs '^nameserver' /system/etc/resolv.conf; then
        echo "AVISO: resolv.conf ausente; revisa el montaje systemless/DNS" >&2
    fi
fi
