# Opciones de montaje de rclone según el perfil de rendimiento y el tipo de
# remoto. Lo cargan mount.sh (para montar) y perf_test.sh (para comprobar que
# el montaje activo usa lo que dice la configuración actual): así el cálculo
# vive en un solo lugar y no pueden desincronizarse.
#
# Requiere MODDIR, RCLONE_CONF y ACTIVE ya definidos. Uso:
#   . "$MODDIR/scripts/perf_opts.sh"
#   compute_mount_opts      # deja el resultado en $MOUNT_OPTS (y $PERF)

# Valor de una clave de la sección de un remoto en rclone.conf (o nada si no
# está). Se hace con "case" y no con sed para no depender de caracteres
# especiales en el nombre. Uso: remote_key <remoto> <clave>
remote_key() {
    in_section=0
    while IFS= read -r line; do
        case "$line" in
            "[$1]") in_section=1 ;;
            "["*"]") in_section=0 ;;
            "$2 "*"="*|"$2="*)
                if [ "$in_section" = 1 ]; then
                    v="${line#*=}"
                    v="${v# }"
                    echo "$v"
                    return
                fi
                ;;
        esac
    done < "$RCLONE_CONF"
}

# Tipo del remoto (ftp, drive, s3...): la clave "type" de su sección.
remote_type() {
    v="$(remote_key "$1" type)"
    echo "${v%% *}"
}

# Carpeta dentro del remoto que se monta (clave "bind_path" que escribe la
# app; en S3 es el bucket, con subcarpeta opcional). Vacía = la raíz del
# remoto. rclone ignora las claves que no conoce, así que no le afecta.
remote_root() {
    remote_key "$1" bind_path
}

compute_mount_opts() {
    # Rendimiento elegido en la app (config/perf: "balanced" o "max") y tamaño
    # de caché en GB (config/cache_gb, opcional; vacío = el de cada perfil).
    PERF="$(cat "$MODDIR/config/perf" 2>/dev/null)"
    CACHE_GB="$(cat "$MODDIR/config/cache_gb" 2>/dev/null)"
    case "$CACHE_GB" in ''|*[!0-9]*|0) CACHE_GB="" ;; esac

    # Extra del perfil Máximo, igual para Drive y FTP:
    #  - multi-thread-streams/cutoff: solo afectan a las copias que hace el
    #    propio rclone (mover/copiar archivos); NO paralelizan las lecturas del
    #    VFS. Se dejan por si el montaje hace copias internas. Lo que paraleliza
    #    lecturas en Drive es --vfs-read-chunk-streams (ver más abajo).
    #  - transfers/checkers: más operaciones de archivo a la vez (los valores
    #    por defecto de rclone son 4 y 8; aquí se triplican).
    #  - vfs-write-back: espera más antes de subir un archivo recién escrito,
    #    para juntar varias escrituras seguidas al mismo archivo en una sola
    #    subida en vez de una por cada una.
    #  - vfs-fast-fingerprint: compara archivos por tamaño en vez de con un
    #    fingerprint más caro de calcular; listados y comparaciones más
    #    ágiles, a costa de no notar por su contenido un archivo modificado
    #    fuera de la app que conserve el mismo tamaño.
    #  - attr-timeout: un juego suele preguntar el tamaño de un archivo antes
    #    de cada lectura; con esto la respuesta sale de la memoria del propio
    #    montaje en vez de ir hasta el remoto cada vez. Seguro aquí porque
    #    nada más escribe en el remoto mientras está montado.
    MAX_EXTRA_OPTS="--multi-thread-streams 4 --multi-thread-cutoff 64M --transfers 12 --checkers 24 --vfs-write-back 15s --vfs-fast-fingerprint --attr-timeout 1h"

    # Drive con cliente propio: el pacer por defecto de rclone limita a unas 10
    # llamadas/s y frena la precarga de miles de archivos pequeños. Con 10 ms
    # de pausa mínima y ráfaga de 200 se aprovecha la cuota del cliente propio.
    DRIVE_PACER_OPTS="--drive-pacer-min-sleep 10ms --drive-pacer-burst 200"

    # Antigüedad máxima de la caché: 720 h (30 días) en todos los perfiles con
    # caché completa. Con valores cortos (1 h en Equilibrado/Drive) rclone
    # purga lo precargado si no se abre a tiempo, y el marcador de
    # preload.sh diría "ya precargado" con la caché vacía. El espacio lo sigue
    # acotando --vfs-cache-max-size.

    # Opciones de montaje según el perfil y el tipo de remoto. Se dejan sin
    # comillas al invocar rclone para que se separen en palabras.
    #  - balanced: lo de siempre (Drive con caché completa de 1G; FTP solo escrituras).
    #  - max: caché completa en ambos, lectura anticipada y listados cacheados más
    #    tiempo. --vfs-cache-min-free-space evita llenar el almacenamiento con la
    #    caché. FTP no avisa de cambios, por eso su dir-cache-time es corto.
    case "$PERF:$(remote_type "$ACTIVE")" in
        max:drive)
            # --vfs-read-chunk-streams baja varios trozos del MISMO archivo en
            # paralelo (requiere rclone 1.67+; se quita solo si el binario es
            # más viejo): es lo que más ayuda a que
            # un juego con muchos archivos medianos (assets de 5-60M, típico
            # en Unity/Unreal) cargue rápido incluso con lecturas salteadas.
            # 6 streams de 32M (192M en vuelo por archivo abierto) rinde mejor
            # que menos streams más grandes en Drive, a costa de más conexiones
            # y RAM simultáneas: si el teléfono tiene poca RAM libre o el juego
            # abre muchos archivos a la vez, conviene bajarlo (por ejemplo a 4).
            MOUNT_OPTS="--vfs-cache-mode full --vfs-cache-max-size ${CACHE_GB:-10}G --vfs-cache-max-age 720h --vfs-cache-min-free-space 2G --vfs-read-ahead 256M --vfs-read-chunk-size 32M --vfs-read-chunk-streams 6 --buffer-size 64M --dir-cache-time 12h --drive-chunk-size 64M $DRIVE_PACER_OPTS $MAX_EXTRA_OPTS"
            ;;
        max:*)
            MOUNT_OPTS="--vfs-cache-mode full --vfs-cache-max-size ${CACHE_GB:-10}G --vfs-cache-max-age 720h --vfs-cache-min-free-space 2G --vfs-read-ahead 128M --buffer-size 32M --dir-cache-time 10m $MAX_EXTRA_OPTS"
            ;;
        *:drive)
            # Drive no admite escritura parcial ni lecturas con salto sobre la
            # nube: la caché completa en disco (acotada) hace que los archivos
            # se comporten como locales para cualquier app.
            MOUNT_OPTS="--vfs-cache-mode full --vfs-cache-max-size ${CACHE_GB:-1}G --vfs-cache-max-age 720h $DRIVE_PACER_OPTS"
            ;;
        *)
            MOUNT_OPTS="--vfs-cache-mode writes"
            ;;
    esac

    # S3: en un bucket las carpetas no existen, solo son parte del nombre de los
    # objetos, así que una carpeta vacía creada desde el explorador se perdía al
    # desmontar. Con --s3-directory-markers rclone sube un objeto vacío
    # "carpeta/" por cada carpeta nueva y la conserva. También permite montar
    # una carpeta vacía del bucket. Requiere rclone 1.64+ (se quita si el
    # binario es más viejo).
    if [ "$(remote_type "$ACTIVE")" = s3 ]; then
        if [ ! -x "$MODDIR/bin/rclone" ] || \
           "$MODDIR/bin/rclone" mount --help 2>&1 | grep -q -- '--s3-directory-markers'; then
            MOUNT_OPTS="$MOUNT_OPTS --s3-directory-markers"
        fi
    fi

    # --vfs-read-chunk-streams solo existe desde rclone 1.67: si el binario
    # incluido es más viejo se quita, en vez de que el montaje falle por una
    # opción desconocida. Mismo cálculo para mount.sh y perf_test.sh.
    case "$MOUNT_OPTS" in
        *--vfs-read-chunk-streams*)
            if [ -x "$MODDIR/bin/rclone" ] && \
               ! "$MODDIR/bin/rclone" mount --help 2>&1 | grep -q -- '--vfs-read-chunk-streams'; then
                MOUNT_OPTS="$(printf '%s' "$MOUNT_OPTS" | sed 's/ --vfs-read-chunk-streams [0-9]*//')"
            fi
            ;;
    esac
}
