#!/system/bin/sh
# Login OAuth de Google Drive dentro del propio dispositivo.
#
# Uso: drive_auth.sh [client_id client_secret]
#
# Ejecuta `rclone authorize drive`: rclone abre un servidor temporal en
# 127.0.0.1:53682, imprime la URL de autorización y, cuando el usuario acepta
# en el navegador del teléfono (Google redirige a ese mismo puerto), imprime
# el token. La app lee la salida (auth.out) por polling; la última línea
# "__EXIT:<código>" indica que rclone terminó. auth.out contiene el token:
# la app lo borra en cuanto lo lee y service.sh lo limpia al arrancar.
SELF="$(readlink -f "$0")"
MODDIR=$(dirname "$(dirname "$SELF")")

# Mismo namespace global que mount.sh, para ver los mismos archivos de sistema.
. "$MODDIR/scripts/common.sh"
enter_global_namespace "$@"

. "$MODDIR/scripts/env.sh"

OUT="$MODDIR/auth.out"
rm -f "$OUT"
umask 077
: > "$OUT"

# Deadline works even on Android builds without timeout.
run_timeout 300 "$MODDIR/bin/rclone" authorize drive "$@" --auth-no-open-browser >> "$OUT" 2>&1
echo "__EXIT:$?" >> "$OUT"
