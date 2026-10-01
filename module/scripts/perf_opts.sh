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

# Versión del binario incluido ("rclone v1.75.1" -> 1 75). Vacío si no se puede leer.
rclone_version_parts() {
    [ -x "$MODDIR/bin/rclone" ] || return 1
    _v="$("$MODDIR/bin/rclone" version 2>/dev/null | head -n 1)"
    _v="${_v#*v}"
    _maj="${_v%%.*}"
    _rest="${_v#*.}"
    _min="${_rest%%.*}"
    case "$_maj$_min" in ''|*[!0-9]*) return 1 ;; esac
    [ -n "$_maj" ] && [ -n "$_min" ] || return 1
    echo "$_maj $_min"
}

rclone_version_known() {
    [ -n "$(rclone_version_parts)" ]
}

# rclone_at_least <mayor> <menor>: el binario es esa versión o una más nueva.
rclone_at_least() {
    set -- "$1" "$2" $(rclone_version_parts)
    [ -n "$3" ] || return 1
    [ "$3" -gt "$1" ] || { [ "$3" -eq "$1" ] && [ "$4" -ge "$2" ]; }
}

# ---- Rendimiento dedicado de S3 (Oracle Cloud y compatibles) ----
# Ajustes opcionales que escribe la app en config/ (vacío o ausente = el valor
# automático de cada proveedor y perfil; los mismos valores están en
# S3Perf, Conf.kt, y deben coincidir):
#   s3_streams        lectura paralela por archivo (1-12)       [Máximo]
#   s3_upload_conc    partes de subida a la vez (1-16)          [Máximo]
#   s3_chunk_mb       tamaño de parte de subida: 8, 16, 32 o 64 [Máximo]
#   s3_fewer_req      1 = menos peticiones, 0 = no
#   s3_dir_cache_min  minutos que se cachea cada listado (1-1440)

# Oracle Cloud si el endpoint es de *.oraclecloud.com, Cloudflare R2 si es de
# *.r2.cloudflarestorage.com; si no, "other".
s3_provider() {
    _ep="$(remote_key "$ACTIVE" endpoint)"
    _ep="${_ep#*://}"
    _ep="${_ep%%/*}"
    _ep="${_ep%%:*}"
    case "$_ep" in
        *.oraclecloud.com) echo oracle ;;
        *.r2.cloudflarestorage.com) echo cloudflare ;;
        *) echo other ;;
    esac
}

# num_in_range <valor> <mín> <máx> <defecto>: el valor si es entero y está en
# el rango; si no, el defecto.
num_in_range() {
    case "$1" in ''|*[!0-9]*) echo "$4"; return ;; esac
    if [ "$1" -lt "$2" ] || [ "$1" -gt "$3" ]; then echo "$4"; else echo "$1"; fi
}

# Opciones de S3 que acepta este binario. "rclone mount --help" no lista las
# de los backends, pero "rclone help flags <regexp>" sí. Si no se puede leer
# (salida vacía) se asume que están todas: el binario del módulo es reciente.
S3_FLAGS_HELP=""
s3_flags_load() {
    [ -x "$MODDIR/bin/rclone" ] || return 0
    S3_FLAGS_HELP="$("$MODDIR/bin/rclone" help flags 's3-|use-server-modtime' 2>&1)"
}
s3_has_flag() {
    [ -z "$S3_FLAGS_HELP" ] && return 0
    printf '%s\n' "$S3_FLAGS_HELP" | grep -q -- "--$1 "
}

# Deja en $MOUNT_OPTS las opciones de un remoto S3.
#  - Equilibrado: caché completa de 1G (como Drive) y listados/peticiones.
#  - Máximo: caché grande, lectura anticipada, varios trozos del mismo archivo
#    en paralelo y subida multiparte en paralelo.
#  - Menos peticiones (por defecto en Oracle, que factura y limita por número
#    de peticiones según el plan, y en Cloudflare R2, que cobra por operación
#    pasado su cupo gratis): --use-server-modtime evita un HEAD por
#    archivo para leer su fecha y el SetModTime (copia en el servidor) tras
#    cada subida, a costa de que la fecha de modificación sea la de subida;
#    --s3-no-head y --s3-no-head-object quitan los HEAD antes/después de
#    subir y bajar.
#  - S3 no avisa de cambios hechos fuera del montaje: el listado solo se
#    refresca al caducar --dir-cache-time.
s3_mount_opts() {
    _prov="$(s3_provider)"
    _cfg="$MODDIR/config"

    _fewer="$(cat "$_cfg/s3_fewer_req" 2>/dev/null)"
    case "$_fewer" in
        0|1) ;;
        *) case "$_prov" in oracle|cloudflare) _fewer=1 ;; *) _fewer=0 ;; esac ;;
    esac

    case "$_prov" in
        oracle|cloudflare) _dcd=30 ;;
        *) if [ "$PERF" = max ]; then _dcd=10; else _dcd=5; fi ;;
    esac
    _dc="$(num_in_range "$(cat "$_cfg/s3_dir_cache_min" 2>/dev/null)" 1 1440 "$_dcd")"

    s3_flags_load
    _o="--vfs-cache-mode full --vfs-cache-max-age 720h --dir-cache-time ${_dc}m"

    if [ "$PERF" = max ]; then
        _st="$(num_in_range "$(cat "$_cfg/s3_streams" 2>/dev/null)" 1 12 6)"
        # R2 falla con la firma en archivos grandes si suben muchas partes a la
        # vez (visto en rclone con 4 o más): arranca en 3. Los demás, en 6.
        if [ "$_prov" = cloudflare ]; then _ccd=3; else _ccd=6; fi
        _cc="$(num_in_range "$(cat "$_cfg/s3_upload_conc" 2>/dev/null)" 1 16 "$_ccd")"
        _ck="$(cat "$_cfg/s3_chunk_mb" 2>/dev/null)"
        case "$_ck" in 8|16|32|64) ;; *) _ck=16 ;; esac
        _tr=4
        # RAM de subida en el peor caso = transfers x partes simultáneas x
        # tamaño de parte. Se acota a 768 MB bajando las partes simultáneas.
        while [ "$_cc" -gt 1 ] && [ $(( _tr * _cc * _ck )) -gt 768 ]; do
            _cc=$(( _cc - 1 ))
        done
        _o="$_o --vfs-cache-max-size ${CACHE_GB:-10}G --vfs-cache-min-free-space 2G --vfs-read-ahead 256M --vfs-read-chunk-size 32M --vfs-read-chunk-streams $_st --buffer-size 64M --transfers $_tr --checkers 8 --vfs-write-back 15s --vfs-fast-fingerprint --attr-timeout 1h"
        s3_has_flag s3-upload-concurrency && _o="$_o --s3-upload-concurrency $_cc"
        s3_has_flag s3-chunk-size && _o="$_o --s3-chunk-size ${_ck}M"
        # Con menos peticiones los archivos medianos suben de una vez (un
        # solo PUT, el umbral por defecto de 200M); si no, desde una parte
        # en adelante se suben en paralelo.
        if [ "$_fewer" = 0 ]; then
            s3_has_flag s3-upload-cutoff && _o="$_o --s3-upload-cutoff ${_ck}M"
        fi
    else
        _o="$_o --vfs-cache-max-size ${CACHE_GB:-1}G"
    fi

    if [ "$_fewer" = 1 ]; then
        s3_has_flag use-server-modtime && _o="$_o --use-server-modtime"
        s3_has_flag s3-no-head && _o="$_o --s3-no-head"
        s3_has_flag s3-no-head-object && _o="$_o --s3-no-head-object"
    fi

    MOUNT_OPTS="$_o"
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
        *:s3)
            # Opciones dedicadas de S3 / Oracle: s3_mount_opts, más arriba.
            s3_mount_opts
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
    # una carpeta vacía del bucket. Requiere rclone 1.64+.
    #
    # OJO: es una opción del backend S3, y "rclone mount --help" solo lista las
    # opciones de montaje/VFS, no las de los backends; buscarla ahí daba siempre
    # "no existe" y la opción se omitía sin avisar. Se decide por la versión del
    # binario y, si no se puede leer, con "rclone help flags".
    if [ "$(remote_type "$ACTIVE")" = s3 ]; then
        if rclone_at_least 1 64 || \
           { ! rclone_version_known && \
             "$MODDIR/bin/rclone" help flags directory-markers 2>&1 | grep -q -- '--s3-directory-markers'; }; then
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
