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
# pierde al desmontar), conserva casi todo su tamaño y el remoto tiene la misma
# cantidad de archivos y el mismo tamaño total que la última precarga
# completa, se omite. clear_cache.sh
# borra esta marca al vaciar la caché, así que un remonte tras limpiarla
# vuelve a precargar todo.
#
# Respeta el tamaño de caché configurado (deja 512 MB de margen) y tiene
# topes de tiempo y de cantidad de archivos por seguridad, para no quedarse
# recorriendo para siempre un remoto con miles de archivos ajenos al juego.
# Si el remoto tiene más contenido que solo los assets, conviene usar la
# opción "carpeta raíz" del servidor Drive para acotar lo que ve la app (y
# por lo tanto lo que esto precarga).
#
# $1 = "force" (opcional): ignora la marca de "ya estaba precargado" y
# vuelve a pasar por todos los archivos seleccionados. La llama así el botón
# "Precargar ahora" de la app; mount.sh la sigue lanzando SIN este argumento
# al montar, para no gastar red de más en cada montaje si no cambió nada. Un
# archivo ya en caché y sin cambios lo sirve rclone desde disco (casi
# instantáneo), así que forzar no vuelve a bajar de la red lo que ya estaba.
SELF="$(readlink -f "$0")"
MODDIR=$(dirname "$(dirname "$SELF")")
FORCE="$1"

if [ "$(readlink /proc/self/ns/mnt 2>/dev/null)" != "$(readlink /proc/1/ns/mnt 2>/dev/null)" ]; then
    exec nsenter -t 1 -m -- sh "$SELF" "$@"
fi

LOG_FILE="$MODDIR/mount.log"
STATUS_FILE="$MODDIR/status.json"
RCLONE_CONF="$MODDIR/config/rclone.conf"
PRELOAD_STATUS="$MODDIR/preload_status.json"

# Restos de una corrida anterior que no terminó bien (el móvil se reinició a
# la mitad, por ejemplo). No son el candado: ese se trata aparte.
rm -f "$MODDIR"/.preload_list "$MODDIR"/.preload_selected "$MODDIR"/.preload_part_* \
      "$MODDIR"/.preload_result_* "$MODDIR"/.preload_progress_* "$MODDIR"/.preload_all_done 2>/dev/null

# Tamaño de un archivo en MB enteros. Se calcula con awk y no con $(( )): el
# mksh de Android hace la aritmética en 32 bits con signo, y un archivo de más
# de 2 GiB daba MB negativos (p. ej. -1581 para uno de 2515 MB).
file_mb() {
    stat -c %s "$1" 2>/dev/null | awk '{printf "%d", $1 / 1048576}'
}

# Escribe preload_status.json de forma atómica (tmp + mv) para que la app,
# que lo lee mientras corre esta precarga, nunca vea un JSON a medio
# escribir. $1 = true/false (sigue corriendo), $2 = archivos hechos, $3 = MB hechos.
write_status() {
    printf '{"running":%s,"remote":"%s","total_files":%s,"selected_files":%s,"selected_mb":%s,"done_files":%s,"done_mb":%s,"updated":%s}\n' \
        "$1" "$ACTIVE" "${TOTAL:-0}" "${N_SELECTED:-0}" "${DONE_MB:-0}" "$2" "$3" "$(date +%s)" \
        > "$PRELOAD_STATUS.tmp" 2>/dev/null && mv "$PRELOAD_STATUS.tmp" "$PRELOAD_STATUS"
}

# Evita dos precargas a la vez (p. ej. dos montajes seguidos). mount.sh ya
# limpia este candado antes de lanzar una nueva, así que uno viejo colgado
# aquí es de un proceso que sigue vivo de verdad.
LOCK="$MODDIR/preload.lock"
mkdir "$LOCK" 2>/dev/null || exit 0
trap '[ -n "$MONITOR_PID" ] && kill "$MONITOR_PID" 2>/dev/null
      rm -rf "$LOCK"
      rm -f "$MODDIR"/.preload_list "$MODDIR"/.preload_selected "$MODDIR"/.preload_part_* \
            "$MODDIR"/.preload_result_* "$MODDIR"/.preload_progress_* "$MODDIR"/.preload_all_done' EXIT INT TERM HUP

ACTIVE="$(sed -n 's/.*"remote":"\([^"]*\)".*/\1/p' "$STATUS_FILE" 2>/dev/null)"
T="$(sed -n 's/.*"target":"\([^"]*\)".*/\1/p' "$STATUS_FILE" 2>/dev/null)"
# Sin remoto o carpeta montada: no hay nada que precargar todavía. Se borra
# cualquier estado de una precarga anterior para que la app no muestre un
# progreso que ya no corresponde a este montaje.
[ -z "$ACTIVE" ] && { rm -f "$PRELOAD_STATUS"; exit 0; }
if [ -z "$T" ] || [ ! -d "$T" ]; then rm -f "$PRELOAD_STATUS"; exit 0; fi

. "$MODDIR/scripts/perf_opts.sh"
compute_mount_opts

case "$MOUNT_OPTS" in
    *"--vfs-cache-mode full"*) ;;
    *) rm -f "$PRELOAD_STATUS"; exit 0 ;;
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

# Tope de tiempo por archivo: 300 s como mínimo, y 4 s por MB para los
# grandes (equivale a aguantar hasta ~0,25 MB/s). Un tope fijo cortaba a
# medias los archivos grandes con enlace lento.
HAVE_TIMEOUT=0
command -v timeout >/dev/null 2>&1 && HAVE_TIMEOUT=1

FILELIST="$MODDIR/.preload_list"
find "$T" -type f -not -path '*/.rclone-bind-test/*' 2>/dev/null | sort > "$FILELIST"
TOTAL="$(wc -l < "$FILELIST" 2>/dev/null | tr -d ' ')"
[ -z "$TOTAL" ] && TOTAL=0

# Huella barata del remoto (cantidad de archivos + KB totales, con "du" en
# una sola pasada) para saber si ya se precargó por completo la vez pasada.
MARKER="$MODDIR/config/preload_done_$ACTIVE"
FP_NOW="$TOTAL $(du -sk "$T" 2>/dev/null | awk '{print $1}')"

# El marcador guarda "<archivos> <KB del remoto> <KB de cache/vfs>". Además de
# que el remoto no haya cambiado, la caché en disco debe seguir ahí (al menos
# el 90 % de lo que había al terminar): rclone la purga por antigüedad
# (--vfs-cache-max-age) o por espacio, y sin esta comprobación se diría "ya
# estaba precargado" con la caché vacía.
CACHE_VFS="$MODDIR/cache/vfs"
MARKER_OK=0
if [ "$FORCE" != "force" ] && [ "$CACHE_IS_RAM" = 0 ] && [ -f "$MARKER" ]; then
    set -- $(cat "$MARKER" 2>/dev/null)
    if [ "$1 $2" = "$FP_NOW" ]; then
        case "$3" in
            ''|*[!0-9]*) ;;
            *)
                CUR_KB="$(du -sk "$CACHE_VFS" 2>/dev/null | awk '{print $1}')"
                case "$CUR_KB" in ''|*[!0-9]*) CUR_KB=0 ;; esac
                [ "$3" -gt 0 ] && [ "$CUR_KB" -ge $(( $3 * 9 / 10 )) ] && MARKER_OK=1
                ;;
        esac
    fi
fi

if [ "$MARKER_OK" = 1 ]; then
    echo "$(date): Precarga: '$ACTIVE' ya estaba precargado por completo (sin cambios), se omite" >> "$LOG_FILE"
    N_SELECTED="$TOTAL"
    DONE_MB="$(( $(printf '%s' "$FP_NOW" | awk '{print $2}') / 1024 ))"
    write_status false "$TOTAL" "$DONE_MB"
    exit 0
fi

# Tope de archivos por corrida (config/preload_max_files; por defecto 20000,
# antes fijo en 2000). Un mod grande de GTA o un juego Unity/Unreal con
# miles de texturas y audios sueltos supera 2000 archivos sin acercarse al
# presupuesto en MB, así que ese tope viejo dejaba assets sin precargar sin
# avisar. Sigue habiendo un tope (y no "sin límite") para no recorrer para
# siempre un remoto ajeno al juego con millones de archivos.
MAX_FILES="$(cat "$MODDIR/config/preload_max_files" 2>/dev/null)"
case "$MAX_FILES" in ''|*[!0-9]*|0) MAX_FILES=20000 ;; esac
[ "$MAX_FILES" -gt 200000 ] && MAX_FILES=200000

echo "$(date): Precarga: '$ACTIVE', $TOTAL archivos, hasta ${BUDGET_MB} MB (tope $MAX_FILES archivos), $WORKERS en paralelo" >> "$LOG_FILE"

# ---- Selección (un solo hilo, sin transferir datos): qué archivos entran
# en el presupuesto.
SELECTED="$MODDIR/.preload_selected"
: > "$SELECTED"
DONE_MB=0
N=0
while IFS= read -r f; do
    N=$(( N + 1 ))
    [ "$N" -gt "$MAX_FILES" ] && break
    SZ_MB="$(file_mb "$f")"; [ -z "$SZ_MB" ] && SZ_MB=0
    [ $(( DONE_MB + SZ_MB )) -gt "$BUDGET_MB" ] && continue
    DONE_MB=$(( DONE_MB + SZ_MB ))
    printf '%s\n' "$f" >> "$SELECTED"
done < "$FILELIST"
N_SELECTED="$(wc -l < "$SELECTED" 2>/dev/null | tr -d ' ')"
[ -z "$N_SELECTED" ] && N_SELECTED=0
write_status true 0 0

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
        SZ_MB="$(file_mb "$f")"; [ -z "$SZ_MB" ] && SZ_MB=0
        t0="$(date +%s)"
        TL=$(( SZ_MB * 4 ))
        [ "$TL" -lt 300 ] && TL=300
        if { if [ "$HAVE_TIMEOUT" = 1 ]; then timeout "$TL" cat "$f"; else cat "$f"; fi; } > /dev/null 2>>"$LOG_FILE"; then
            ok=$(( ok + 1 ))
            echo "$(date): Precarga[$2]: ${f#$T/} (${SZ_MB} MB, $(( $(date +%s) - t0 ))s)" >> "$LOG_FILE"
            # Solo este worker escribe en su propio archivo: sin condiciones
            # de carrera entre workers. Lo lee el monitor de progreso.
            echo "$SZ_MB" >> "$MODDIR/.preload_progress_$2"
        else
            echo "$(date): Precarga[$2]: falló ${f#$T/}" >> "$LOG_FILE"
        fi
    done < "$1"
    echo "$ok" > "$MODDIR/.preload_result_$2"
}

# Progreso en vivo para la app (SectionCard "Precarga para juegos" en
# Inicio): suma cada 2s lo que los workers llevan hecho y lo publica en
# preload_status.json. Corre en paralelo a los workers y se apaga solo al
# ver .preload_all_done (lo crea este script justo después de "wait").
(
    while [ ! -f "$MODDIR/.preload_all_done" ]; do
        DF=0
        DMB=0
        for pf in "$MODDIR"/.preload_progress_*; do
            [ -f "$pf" ] || continue
            n="$(wc -l < "$pf" 2>/dev/null | tr -d ' ')"
            case "$n" in ''|*[!0-9]*) n=0 ;; esac
            DF=$(( DF + n ))
            s="$(awk '{sum+=$1} END{print sum+0}' "$pf" 2>/dev/null)"
            case "$s" in ''|*[!0-9]*) s=0 ;; esac
            DMB=$(( DMB + s ))
        done
        write_status true "$DF" "$DMB"
        sleep 2
    done
) &
MONITOR_PID=$!

# Se guardan los PIDs de los workers: un "wait" sin argumentos esperaría
# también al monitor de progreso, que solo termina cuando existe
# .preload_all_done (que se crea después del wait) y el script se colgaría
# para siempre. Si no hay ningún worker no se espera a nada.
WPIDS=""
w=0
while [ "$w" -lt "$WORKERS" ]; do
    PART="$MODDIR/.preload_part_$w"
    if [ -s "$PART" ]; then
        ( preload_worker "$PART" "$w" ) &
        WPIDS="$WPIDS $!"
    fi
    w=$(( w + 1 ))
done
[ -n "$WPIDS" ] && wait $WPIDS
touch "$MODDIR/.preload_all_done"
wait "$MONITOR_PID" 2>/dev/null

PN=0
for rf in "$MODDIR"/.preload_result_*; do
    [ -f "$rf" ] || continue
    v="$(cat "$rf" 2>/dev/null)"
    case "$v" in ''|*[!0-9]*) ;; *) PN=$(( PN + v )) ;; esac
done

# MB realmente bajados (suma final de lo que cada worker fue anotando), no el
# presupuesto ($DONE_MB de la selección): si algún archivo falló, difieren.
FINAL_MB=0
for pf in "$MODDIR"/.preload_progress_*; do
    [ -f "$pf" ] || continue
    s="$(awk '{sum+=$1} END{print sum+0}' "$pf" 2>/dev/null)"
    case "$s" in ''|*[!0-9]*) ;; *) FINAL_MB=$(( FINAL_MB + s )) ;; esac
done
write_status false "$PN" "$FINAL_MB"

echo "$(date): Precarga terminada: ${FINAL_MB} de ${DONE_MB} MB, $PN de $N_SELECTED archivos ($TOTAL en total)" >> "$LOG_FILE"

# Marca de "precarga completa" solo si de verdad se cubrió todo el remoto
# (nada se salteó por presupuesto ni falló). Así el próximo montaje, si nada
# cambió, no vuelve a bajar lo que ya está en disco.
if [ "$CACHE_IS_RAM" = 0 ] && [ "$PN" = "$TOTAL" ] && [ "$TOTAL" -gt 0 ]; then
    mkdir -p "$MODDIR/config" 2>/dev/null
    sync
    echo "$FP_NOW $(du -sk "$CACHE_VFS" 2>/dev/null | awk '{print $1+0}')" > "$MARKER"
fi
