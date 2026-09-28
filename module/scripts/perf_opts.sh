# Opciones de montaje de rclone según el perfil de rendimiento y el tipo de
# remoto. Lo cargan mount.sh (para montar) y perf_test.sh (para comprobar que
# el montaje activo usa lo que dice la configuración actual): así el cálculo
# vive en un solo lugar y no pueden desincronizarse.
#
# Requiere MODDIR, RCLONE_CONF y ACTIVE ya definidos. Uso:
#   . "$MODDIR/scripts/perf_opts.sh"
#   compute_mount_opts      # deja el resultado en $MOUNT_OPTS (y $PERF)

# Tipo del remoto (ftp, drive...): lee la clave "type" de su sección. Se hace
# con "case" y no con sed para no depender de caracteres especiales en el
# nombre.
remote_type() {
    in_section=0
    while IFS= read -r line; do
        case "$line" in
            "[$1]") in_section=1 ;;
            "["*"]") in_section=0 ;;
            "type "*"="*|"type="*)
                if [ "$in_section" = 1 ]; then
                    v="${line#*=}"
                    v="${v# }"
                    echo "${v%% *}"
                    return
                fi
                ;;
        esac
    done < "$RCLONE_CONF"
}

compute_mount_opts() {
    # Rendimiento elegido en la app (config/perf: "balanced" o "max") y tamaño
    # de caché en GB (config/cache_gb, opcional; vacío = el de cada perfil).
    PERF="$(cat "$MODDIR/config/perf" 2>/dev/null)"
    CACHE_GB="$(cat "$MODDIR/config/cache_gb" 2>/dev/null)"
    case "$CACHE_GB" in ''|*[!0-9]*|0) CACHE_GB="" ;; esac

    # Opciones de montaje según el perfil y el tipo de remoto. Se dejan sin
    # comillas al invocar rclone para que se separen en palabras.
    #  - balanced: lo de siempre (Drive con caché completa de 1G; FTP solo escrituras).
    #  - max: caché completa en ambos, lectura anticipada y listados cacheados más
    #    tiempo. --vfs-cache-min-free-space evita llenar el almacenamiento con la
    #    caché. FTP no avisa de cambios, por eso su dir-cache-time es corto.
    case "$PERF:$(remote_type "$ACTIVE")" in
        max:drive)
            MOUNT_OPTS="--vfs-cache-mode full --vfs-cache-max-size ${CACHE_GB:-10}G --vfs-cache-max-age 24h --vfs-cache-min-free-space 2G --vfs-read-ahead 256M --vfs-read-chunk-size 64M --vfs-read-chunk-streams 4 --buffer-size 64M --dir-cache-time 12h --drive-chunk-size 64M"
            ;;
        max:*)
            MOUNT_OPTS="--vfs-cache-mode full --vfs-cache-max-size ${CACHE_GB:-10}G --vfs-cache-max-age 12h --vfs-cache-min-free-space 2G --vfs-read-ahead 128M --buffer-size 32M --dir-cache-time 10m"
            ;;
        *:drive)
            # Drive no admite escritura parcial ni lecturas con salto sobre la
            # nube: la caché completa en disco (acotada) hace que los archivos
            # se comporten como locales para cualquier app.
            MOUNT_OPTS="--vfs-cache-mode full --vfs-cache-max-size ${CACHE_GB:-1}G --vfs-cache-max-age 1h"
            ;;
        *)
            MOUNT_OPTS="--vfs-cache-mode writes"
            ;;
    esac
}
