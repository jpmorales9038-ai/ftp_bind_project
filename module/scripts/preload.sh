#!/system/bin/sh
# Precarga automática: tras un montaje correcto, si el perfil cachea
# lecturas completas (Máximo, o Google Drive en cualquier perfil), baja a la
# caché los archivos del remoto montado. Así, cuando el juego (u otra app)
# los abra por primera vez, ya están locales en vez de tener que esperar la
# descarga en ese momento.
#
# La lanza mount.sh en segundo plano justo después de "montado
# correctamente"; no bloquea eso ni el resto del arranque. No hace nada si
# el perfil activo no cachea lecturas completas (FTP en Equilibrado): ahí
# leer un archivo entero no deja nada permanente en caché, sería red
# desperdiciada.
#
# Respeta el tamaño de caché configurado (deja 512 MB de margen) y tiene
# topes de tiempo y de cantidad de archivos por seguridad, para no quedarse
# recorriendo para siempre un remoto con miles de archivos ajenos al juego.
# Si el remoto tiene más contenido que solo los assets, conviene usar la
# opción "carpeta raíz" del servidor Drive para acotar lo que ve la app (y
# por lo tanto lo que esto precarga).
SELF="$(readlink -f "$0")"
MODDIR=$(dirname "$(dirname "$SELF")")

if [ "$(readlink /proc/self/ns/mnt 2>/dev/null)" != "$(readlink /proc/1/ns/mnt 2>/dev/null)" ]; then
    exec nsenter -t 1 -m -- sh "$SELF" "$@"
fi

LOG_FILE="$MODDIR/mount.log"
STATUS_FILE="$MODDIR/status.json"
RCLONE_CONF="$MODDIR/config/rclone.conf"

# Evita dos precargas a la vez (p. ej. dos montajes seguidos). mount.sh ya
# limpia este candado antes de lanzar una nueva, así que uno viejo colgado
# aquí es de un proceso que sigue vivo de verdad.
LOCK="$MODDIR/preload.lock"
mkdir "$LOCK" 2>/dev/null || exit 0
trap 'rm -rf "$LOCK"' EXIT INT TERM HUP

ACTIVE="$(sed -n 's/.*"remote":"\([^"]*\)".*/\1/p' "$STATUS_FILE" 2>/dev/null)"
T="$(sed -n 's/.*"target":"\([^"]*\)".*/\1/p' "$STATUS_FILE" 2>/dev/null)"
[ -z "$ACTIVE" ] && exit 0
[ -z "$T" ] || [ ! -d "$T" ] && exit 0

. "$MODDIR/scripts/perf_opts.sh"
compute_mount_opts

case "$MOUNT_OPTS" in
    *"--vfs-cache-mode full"*) ;;
    *) exit 0 ;;
esac

case "$PERF:$(remote_type "$ACTIVE")" in
    max:*) DEFAULT_GB=10 ;;
    *) DEFAULT_GB=1 ;;
esac
BUDGET_MB=$(( ${CACHE_GB:-$DEFAULT_GB} * 1024 - 512 ))
[ "$BUDGET_MB" -lt 256 ] && BUDGET_MB=256

LIMIT=""
command -v timeout >/dev/null 2>&1 && LIMIT="timeout 300"

FILELIST="$MODDIR/.preload_list"
find "$T" -type f -not -path '*/.rclone-bind-test/*' 2>/dev/null > "$FILELIST"
TOTAL="$(wc -l < "$FILELIST" 2>/dev/null | tr -d ' ')"
[ -z "$TOTAL" ] && TOTAL=0

echo "$(date): Precarga: '$ACTIVE', $TOTAL archivos, hasta ${BUDGET_MB} MB" >> "$LOG_FILE"

DONE_MB=0
N=0
PN=0
while IFS= read -r f; do
    N=$(( N + 1 ))
    [ "$N" -gt 2000 ] && break
    SZ_MB=$(( $(stat -c %s "$f" 2>/dev/null || echo 0) / 1048576 ))
    if [ $(( DONE_MB + SZ_MB )) -gt "$BUDGET_MB" ]; then
        continue
    fi
    t0="$(date +%s)"
    if $LIMIT cat "$f" > /dev/null 2>>"$LOG_FILE"; then
        DONE_MB=$(( DONE_MB + SZ_MB ))
        PN=$(( PN + 1 ))
        echo "$(date): Precarga: ${f#$T/} (${SZ_MB} MB, $(( $(date +%s) - t0 ))s)" >> "$LOG_FILE"
    else
        echo "$(date): Precarga: falló ${f#$T/}" >> "$LOG_FILE"
    fi
done < "$FILELIST"
rm -f "$FILELIST"

echo "$(date): Precarga terminada: ${DONE_MB} MB en $PN de $N archivos" >> "$LOG_FILE"
