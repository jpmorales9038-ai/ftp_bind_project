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
# Descarga en paralelo (scripts/config/preload_workers, por defecto 4,
# tope 8): cada "cat" abre su propia conexión, así que leer varios archivos
# a la vez aprovecha mucho mejor el ancho de banda que uno por uno, sobre
# todo con juegos que tienen miles de archivos pequeños (que no llegan al
# tamaño mínimo de --vfs-read-chunk-size / --multi-thread-cutoff como para
# paralelizarse por sí solos dentro de rclone). La selección de qué archivos
# entran en el presupuesto se decide antes, en un solo hilo, para que repartir
# el trabajo entre los workers no tenga condiciones de carrera.
#
# No repite trabajo ya hecho: si la caché sigue en disco (no en RAM, que se
# pierde al desmontar) y el remoto tiene la misma cantidad de archivos y el
# mismo tamaño total que la última precarga completa, se omite. clear_cache.sh
# borra esta marca al vaciar la caché, así que un remonte tras limpiarla
# vuelve a precargar todo.
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

# Restos de una corrida anterior que no terminó bien (el móvil se reinició a
# la mitad, por ejemplo). No son el candado: ese se trata aparte.
rm -f "$MODDIR"/.preload_list "$MODDIR"/.preload_selected "$MODDIR"/.preload_part_* "$MODDIR"/.preload_result_* 2>/dev/null

# Evita dos precargas a la vez (p. ej. dos montajes seguidos). mount.sh ya
# limpia este candado antes de lanzar una nueva, así que uno viejo colgado
# aquí es de un proceso que sigue vivo de verdad.
LOCK="$MODDIR/preload.lock"
mkdir "$LOCK" 2>/dev/null || exit 0
trap 'rm -rf "$LOCK"; rm -f "$MODDIR"/.preload_list "$MODDIR"/.preload_selected "$MODDIR"/.preload_part_* "$MODDIR"/.preload_result_*' EXIT INT TERM HUP

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

# La caché en RAM (tmpfs) se pierde al desmontar o reiniciar: nunca se puede
# saltar la precarga ahí, siempre está fría al empezar.
CACHE_IS_RAM=0
grep -q " $MODDIR/cache_ram tmpfs" /proc/mounts 2>/dev/null && CACHE_IS_RAM=1

case "$PERF:$(remote_type "$ACTIVE")" in
    max:*) DEFAULT_GB=10 ;;
    *) DEFAULT_GB=1 ;;
esac
BUDGET_MB=$(( ${CACHE_GB:-$DEFAULT_GB} * 1024 - 512 ))
[ "$BUDGET_MB" -lt 256 ] && BUDGET_MB=256

# Workers de descarga en paralelo (config/preload_workers; 1-8, por defecto 4).
WORKERS="$(cat "$MODDIR/config/preload_workers" 2>/dev/null)"
case "$WORKERS" in ''|*[!0-9]*|0) WORKERS=4 ;; esac
[ "$WORKERS" -gt 8 ] && WORKERS=8

LIMIT=""
command -v timeout >/dev/null 2>&1 && LIMIT="timeout 300"

FILELIST="$MODDIR/.preload_list"
find "$T" -type f -not -path '*/.rclone-bind-test/*' 2>/dev/null | sort > "$FILELIST"
TOTAL="$(wc -l < "$FILELIST" 2>/dev/null | tr -d ' ')"
[ -z "$TOTAL" ] && TOTAL=0

# Huella barata del remoto (cantidad de archivos + KB totales, con "du" en
# una sola pasada) para saber si ya se precargó por completo la vez pasada.
MARKER="$MODDIR/config/preload_done_$ACTIVE"
FP_NOW="$TOTAL $(du -sk "$T" 2>/dev/null | awk '{print $1}')"

if [ "$CACHE_IS_RAM" = 0 ] && [ -f "$MARKER" ] && [ "$(cat "$MARKER" 2>/dev/null)" = "$FP_NOW" ]; then
    echo "$(date): Precarga: '$ACTIVE' ya estaba precargado por completo (sin cambios), se omite" >> "$LOG_FILE"
    exit 0
fi

echo "$(date): Precarga: '$ACTIVE', $TOTAL archivos, hasta ${BUDGET_MB} MB, $WORKERS en paralelo" >> "$LOG_FILE"

# ---- Selección (un solo hilo, sin transferir datos): qué archivos entran
# en el presupuesto, respetando el mismo tope de 2000 archivos de siempre.
SELECTED="$MODDIR/.preload_selected"
: > "$SELECTED"
DONE_MB=0
N=0
while IFS= read -r f; do
    N=$(( N + 1 ))
    [ "$N" -gt 2000 ] && break
    SZ_MB=$(( $(stat -c %s "$f" 2>/dev/null || echo 0) / 1048576 ))
    [ $(( DONE_MB + SZ_MB )) -gt "$BUDGET_MB" ] && continue
    DONE_MB=$(( DONE_MB + SZ_MB ))
    printf '%s\n' "$f" >> "$SELECTED"
done < "$FILELIST"

# ---- Reparto entre workers (round-robin, sin condiciones de carrera: cada
# archivo va a un único archivo de partición antes de arrancar nada).
i=0
while IFS= read -r f; do
    part=$(( i % WORKERS ))
    printf '%s\n' "$f" >> "$MODDIR/.preload_part_$part"
    i=$(( i + 1 ))
done < "$SELECTED"

preload_worker() {
    # $1 = archivo con las rutas de este worker; $2 = número del worker (solo para el log)
    ok=0
    while IFS= read -r f; do
        SZ_MB=$(( $(stat -c %s "$f" 2>/dev/null || echo 0) / 1048576 ))
        t0="$(date +%s)"
        if $LIMIT cat "$f" > /dev/null 2>>"$LOG_FILE"; then
            ok=$(( ok + 1 ))
            echo "$(date): Precarga[$2]: ${f#$T/} (${SZ_MB} MB, $(( $(date +%s) - t0 ))s)" >> "$LOG_FILE"
        else
            echo "$(date): Precarga[$2]: falló ${f#$T/}" >> "$LOG_FILE"
        fi
    done < "$1"
    echo "$ok" > "$MODDIR/.preload_result_$2"
}

w=0
while [ "$w" -lt "$WORKERS" ]; do
    PART="$MODDIR/.preload_part_$w"
    [ -s "$PART" ] && ( preload_worker "$PART" "$w" ) &
    w=$(( w + 1 ))
done
wait

PN=0
for rf in "$MODDIR"/.preload_result_*; do
    [ -f "$rf" ] || continue
    v="$(cat "$rf" 2>/dev/null)"
    case "$v" in ''|*[!0-9]*) ;; *) PN=$(( PN + v )) ;; esac
done

echo "$(date): Precarga terminada: ${DONE_MB} MB en $PN de $N archivos ($TOTAL en total)" >> "$LOG_FILE"

# Marca de "precarga completa" solo si de verdad se cubrió todo el remoto
# (nada se salteó por presupuesto ni falló). Así el próximo montaje, si nada
# cambió, no vuelve a bajar lo que ya está en disco.
if [ "$CACHE_IS_RAM" = 0 ] && [ "$PN" = "$TOTAL" ] && [ "$TOTAL" -gt 0 ]; then
    mkdir -p "$MODDIR/config" 2>/dev/null
    echo "$FP_NOW" > "$MARKER"
fi
