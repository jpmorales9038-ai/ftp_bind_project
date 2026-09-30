package com.rclonebind.app.root

import com.topjohnwu.superuser.Shell

/**
 * Rutas del módulo KernelSU. Ajustar <MODULE_ID> al id real definido en module.prop.
 */
object ModulePaths {
    const val MODULE_ID = "rclone_ftp_bind"
    const val BASE = "/data/adb/modules/$MODULE_ID"
    const val BIN = "$BASE/bin/rclone"
    const val SCRIPTS = "$BASE/scripts"
    const val CONFIG_DIR = "$BASE/config"
    const val RCLONE_CONF = "$CONFIG_DIR/rclone.conf"
    const val ACTIVE_FILE = "$CONFIG_DIR/active"
    const val TARGET_PATH_FILE = "$CONFIG_DIR/target_path"
    const val PERF_FILE = "$CONFIG_DIR/perf"
    const val CACHE_GB_FILE = "$CONFIG_DIR/cache_gb"
    const val RAM_CACHE_FILE = "$CONFIG_DIR/ram_cache"
    const val STATUS_FILE = "$BASE/status.json"
    const val LOG_FILE = "$BASE/mount.log"
    /** Progreso de la precarga de assets (lo escribe scripts/preload.sh). */
    const val PRELOAD_STATUS_FILE = "$BASE/preload_status.json"
    /** Salida temporal de `rclone authorize` (contiene el token: se borra al terminar). */
    const val AUTH_OUT = "$BASE/auth.out"
    /** Progreso de la prueba de rendimiento (lo escribe scripts/perf_test.sh). */
    const val PERF_OUT = "$BASE/perf_test.out"
}

/**
 * Ejecuta los scripts del módulo vía su. Cada función devuelve stdout+stderr
 * combinados para poder mostrarlos en la pantalla de Logs.
 */
object RootShell {

    // La configuración del Shell.Builder (flags, logging) se fija una sola vez
    // en RCloneApp, antes de que exista el shell principal. No repetir aquí.

    private fun sq(s: String) = "'" + s.replace("'", "'\\''") + "'"

    private fun run(cmd: String): Result {
        val result = Shell.cmd(cmd).exec()
        return Result(result.isSuccess, result.out.joinToString("\n"))
    }

    /** Primera línea de `rclone version` (ej. "rclone v1.68.0"), o null si no hay root / binario. */
    fun rcloneVersion(): String? =
        Shell.cmd("${ModulePaths.BIN} version 2>/dev/null | head -n 1").exec().out
            .firstOrNull { it.isNotBlank() }?.trim()

    fun mount(): Result = run("sh ${ModulePaths.SCRIPTS}/mount.sh")

    fun unmount(): Result = run("sh ${ModulePaths.SCRIPTS}/unmount.sh")

    fun status(): Result = run("cat ${ModulePaths.STATUS_FILE} 2>/dev/null || echo '{\"mounted\":false}'")

    // ---- Precarga de assets (perfil Máximo / Drive): ver scripts/preload.sh ----

    /**
     * Relanza la precarga aunque ya haya una corriendo o recién terminada.
     * Corta cualquier corrida anterior primero (igual que hace mount.sh al
     * montar), así sirve tanto para reintentar un fallo como para volver a
     * comprobar el remoto tras agregar archivos nuevos.
     *
     * Espera hasta 5s (en segundo plano, sin bloquear esta llamada) a que
     * esa corrida anterior suelte su candado de verdad antes de borrarlo y
     * lanzar la nueva: borrarlo sin esperar podía dejar la precarga nueva
     * pisando archivos temporales a medio limpiar de la vieja y terminando
     * con 0 archivos seleccionados (ver el mismo ajuste en mount.sh).
     *
     * Va con "force": ignora la marca de "ya estaba precargado" (que
     * mount.sh sí respeta al montar solo, para no gastar red de más en cada
     * montaje) porque acá el usuario tocó el botón a propósito. No vuelve a
     * bajar de la red lo que ya está en caché sin cambios: rclone lo sirve
     * desde disco.
     */
    fun preloadStart(): Result = run(
        "pkill -f ${ModulePaths.SCRIPTS}/preload.sh 2>/dev/null; " +
            "( i=0; while [ -d ${ModulePaths.BASE}/preload.lock ] && [ \"\$i\" -lt 5 ]; do sleep 1; i=\$((i + 1)); done; " +
            "rm -rf ${ModulePaths.BASE}/preload.lock; " +
            "nohup sh ${ModulePaths.SCRIPTS}/preload.sh force >/dev/null 2>&1 ) &"
    )

    fun preloadStatus(): String =
        Shell.cmd("cat ${ModulePaths.PRELOAD_STATUS_FILE} 2>/dev/null").exec().out.joinToString("\n")

    fun tailLog(lines: Int = 200): Result = run("tail -n $lines ${ModulePaths.LOG_FILE} 2>/dev/null")

    /**
     * Vacía el log sin borrar el archivo: rclone lo mantiene abierto en modo
     * append (--log-file), así que truncarlo es seguro y sigue escribiendo
     * al final del archivo ya vacío.
     */
    fun clearLog(): Result = run(": > ${ModulePaths.LOG_FILE}")

    // ---- Servidores (una sección [nombre] por servidor en rclone.conf) ----

    private fun readConf(): Conf {
        val out = Shell.cmd("cat ${ModulePaths.RCLONE_CONF} 2>/dev/null").exec().out
        return parseConf(out.joinToString("\n"))
    }

    private fun writeConf(conf: Conf): Result =
        run(
            "mkdir -p ${ModulePaths.CONFIG_DIR} && " +
                "printf '%s' ${sq(serializeConf(conf))} > ${ModulePaths.RCLONE_CONF}.tmp && " +
                "chmod 600 ${ModulePaths.RCLONE_CONF}.tmp && " +
                "mv ${ModulePaths.RCLONE_CONF}.tmp ${ModulePaths.RCLONE_CONF}"
        )

    fun loadProfiles(): List<RemoteProfile> = readConf().toProfiles()

    /**
     * Crea el servidor, o lo edita si [original] no es null (con [name]
     * distinto es un renombrado). Con [pass] vacío al editar se conserva la
     * contraseña que ya estaba guardada.
     */
    fun saveProfile(original: String?, name: String, host: String, port: String, user: String, pass: String): Result {
        val conf = readConf()
        if (name != original && conf.containsKey(name)) {
            return Result(false, "Ya existe un servidor llamado $name")
        }
        val old = original?.let { conf[it] }
        var obscured: String? = old?.get("pass")

        // Sin contraseña (FTP anónimo / abierto) también hay que guardar el
        // valor ofuscado de la cadena vacía: rclone hace Reveal() del campo
        // "pass" al crear el remoto y con la clave ausente/vacía falla con
        // "NewFS decrypt password: input too short when revealing password".
        if (pass.isNotEmpty() || obscured.isNullOrEmpty()) {
            // rclone espera la contraseña "ofuscada" (reversible, no es
            // cifrado). stderr va a /dev/null porque la app mezcla stderr con
            // stdout; se toma la última línea no vacía y se valida el formato
            // (base64url de IV de 16 bytes + datos => mínimo 22 caracteres).
            val obscure = Shell.cmd("${ModulePaths.BIN} obscure ${sq(pass)} 2>/dev/null").exec()
            if (!obscure.isSuccess) {
                return Result(false, "No se pudo ofuscar la contraseña: " + obscure.out.joinToString("\n"))
            }
            val value = obscure.out.lastOrNull { it.isNotBlank() }?.trim().orEmpty()
            if (!Regex("^[A-Za-z0-9_-]{22,}$").matches(value)) {
                return Result(false, "rclone obscure devolvió un valor inválido: ${value.take(60)}")
            }
            obscured = value
        }

        val section = LinkedHashMap<String, String>()
        section["type"] = "ftp"
        section["host"] = host
        section["port"] = port
        section["user"] = user
        if (obscured != null) section["pass"] = obscured
        // Conserva claves extra que el usuario haya puesto a mano (tls, etc.)
        if (old != null) {
            for ((k, v) in old) {
                if (!section.containsKey(k)) section[k] = v
            }
        }

        return writeConf(putSection(conf, original, name, section))
    }

    /** Coloca [section] como [name]: reemplaza a [original] (renombrado) o se agrega al final. */
    private fun putSection(conf: Conf, original: String?, name: String, section: LinkedHashMap<String, String>): Conf {
        val out = Conf()
        var placed = false
        for ((k, v) in conf) {
            if (original != null && k == original) {
                out[name] = section
                placed = true
            } else {
                out[k] = v
            }
        }
        if (!placed) out[name] = section
        return out
    }

    // Claves que la app administra en un remoto Drive; el resto de claves que
    // el usuario haya puesto a mano (impersonate, export_formats...) se conservan.
    private val DRIVE_MANAGED_KEYS =
        setOf("type", "client_id", "client_secret", "scope", "token", "root_folder_id", "team_drive", "acknowledge_abuse")

    /**
     * Crea o edita un remoto Google Drive. Con [token] null al editar se
     * conserva la sesión que ya estaba guardada.
     */
    fun saveDriveProfile(original: String?, name: String, token: String?, options: DriveOptions): Result {
        val conf = readConf()
        if (name != original && conf.containsKey(name)) {
            return Result(false, "Ya existe un servidor llamado $name")
        }
        val old = original?.let { conf[it] }
        val finalToken = token ?: old?.get("token")
        if (finalToken.isNullOrEmpty()) {
            return Result(false, "Falta iniciar sesión con Google")
        }

        val section = LinkedHashMap<String, String>()
        section["type"] = RemoteType.DRIVE.rclone
        if (options.clientId.isNotEmpty()) section["client_id"] = options.clientId
        if (options.clientSecret.isNotEmpty()) section["client_secret"] = options.clientSecret
        section["scope"] = if (options.readOnly) DRIVE_SCOPE_READONLY else DRIVE_SCOPE_FULL
        section["token"] = finalToken
        if (options.rootFolderId.isNotEmpty()) section["root_folder_id"] = options.rootFolderId
        if (options.teamDrive.isNotEmpty()) section["team_drive"] = options.teamDrive
        // Opción del backend drive: rclone la lee directo del rclone.conf, sin tocar mount.sh.
        if (options.acknowledgeAbuse) section["acknowledge_abuse"] = "true"
        if (old != null) {
            for ((k, v) in old) {
                if (k !in DRIVE_MANAGED_KEYS && !section.containsKey(k)) section[k] = v
            }
        }
        return writeConf(putSection(conf, original, name, section))
    }

    fun deleteProfile(name: String): Result {
        val conf = readConf()
        conf.remove(name)
        return writeConf(conf)
    }

    // ---- Google Drive: login OAuth dentro del dispositivo ----

    /**
     * Lanza `rclone authorize drive` en segundo plano (scripts/drive_auth.sh).
     * Vuelve enseguida; el progreso se lee con [driveAuthOutput]. Con
     * [clientId] y [clientSecret] propios se usa ese cliente OAuth en vez del
     * compartido de rclone.
     */
    fun driveAuthStart(clientId: String, clientSecret: String): Result {
        val args = if (clientId.isNotEmpty() && clientSecret.isNotEmpty()) " ${sq(clientId)} ${sq(clientSecret)}" else ""
        return run("nohup sh ${ModulePaths.SCRIPTS}/drive_auth.sh$args >/dev/null 2>&1 &")
    }

    fun driveAuthOutput(): String =
        Shell.cmd("cat ${ModulePaths.AUTH_OUT} 2>/dev/null").exec().out.joinToString("\n")

    /** Corta el login (si sigue vivo) y borra la salida temporal con el token. */
    fun driveAuthStop(): Result =
        run("pkill -f drive_auth.sh; pkill -f 'rclone authorize'; rm -f ${ModulePaths.AUTH_OUT}")

    /** Lista la raíz del remoto para confirmar que la sesión y la red funcionan. */
    fun checkRemote(name: String): Result = run("sh ${ModulePaths.SCRIPTS}/check_remote.sh ${sq(name)}")

    fun readActive(): String? =
        Shell.cmd("cat ${ModulePaths.ACTIVE_FILE} 2>/dev/null").exec().out
            .joinToString("").trim().ifEmpty { null }

    fun setActive(name: String): Result =
        run("mkdir -p ${ModulePaths.CONFIG_DIR} && printf '%s' ${sq(name)} > ${ModulePaths.ACTIVE_FILE}")

    fun readTargetPath(): String =
        Shell.cmd("cat ${ModulePaths.TARGET_PATH_FILE} 2>/dev/null").exec().out
            .joinToString("").trim().ifEmpty { DEFAULT_TARGET_PATH }

    fun setTargetPath(path: String): Result =
        run("mkdir -p ${ModulePaths.CONFIG_DIR} && printf '%s' ${sq(path)} > ${ModulePaths.TARGET_PATH_FILE}")

    fun readPerfMode(): PerfMode {
        val v = Shell.cmd("cat ${ModulePaths.PERF_FILE} 2>/dev/null").exec().out.joinToString("").trim()
        return PerfMode.entries.firstOrNull { it.id == v } ?: PerfMode.BALANCED
    }

    fun setPerfMode(mode: PerfMode): Result =
        run("mkdir -p ${ModulePaths.CONFIG_DIR} && printf '%s' ${sq(mode.id)} > ${ModulePaths.PERF_FILE}")

    /** Tamaño de caché elegido (GB), o null si se usa el del perfil. */
    fun readCacheGb(): Int? =
        Shell.cmd("cat ${ModulePaths.CACHE_GB_FILE} 2>/dev/null").exec().out
            .joinToString("").trim().toIntOrNull()?.takeIf { it in CACHE_GB_MIN..CACHE_GB_MAX }

    /** Con [gb] null se borra el ajuste y vuelve al tamaño del perfil. */
    fun setCacheGb(gb: Int?): Result =
        if (gb == null) run("rm -f ${ModulePaths.CACHE_GB_FILE}")
        else run("mkdir -p ${ModulePaths.CONFIG_DIR} && printf '%s' $gb > ${ModulePaths.CACHE_GB_FILE}")

    /**
     * Caché en RAM del perfil Máximo. Guarda solo lo que el usuario pidió:
     * mount.sh decide con root, al montar, si hay memoria libre para
     * cumplirlo (si no, sigue en disco y lo anota en Logs).
     */
    fun readRamCache(): Boolean =
        Shell.cmd("cat ${ModulePaths.RAM_CACHE_FILE} 2>/dev/null").exec().out.joinToString("").trim() == "1"

    fun setRamCache(enabled: Boolean): Result =
        if (enabled) run("mkdir -p ${ModulePaths.CONFIG_DIR} && printf '1' > ${ModulePaths.RAM_CACHE_FILE}")
        else run("rm -f ${ModulePaths.RAM_CACHE_FILE}")

    // ---- Prueba de rendimiento ----

    /**
     * Lanza scripts/perf_test.sh en segundo plano. Vuelve enseguida; el
     * progreso se lee con [perfTestOutput]. Borra la salida anterior primero
     * para no leer los resultados de una prueba vieja.
     */
    fun perfTestStart(): Result =
        run("rm -f ${ModulePaths.PERF_OUT}; nohup sh ${ModulePaths.SCRIPTS}/perf_test.sh >/dev/null 2>&1 &")

    fun perfTestOutput(): String =
        Shell.cmd("cat ${ModulePaths.PERF_OUT} 2>/dev/null").exec().out.joinToString("\n")

    /** Corta la prueba si sigue viva (el script borra su archivo temporal al recibir la señal). */
    fun perfTestStop(): Result =
        run("pkill -f perf_test.sh; rm -f ${ModulePaths.PERF_OUT}")

    fun readAutostart(): Boolean =
        Shell.cmd("cat ${ModulePaths.CONFIG_DIR}/autostart 2>/dev/null").exec().out
            .joinToString("").trim() == "1"

    fun setAutostart(enabled: Boolean): Result =
        run("mkdir -p ${ModulePaths.CONFIG_DIR} && echo '${if (enabled) "1" else "0"}' > ${ModulePaths.CONFIG_DIR}/autostart")

    // ---- Caché en disco de rclone ----

    /** Tamaño actual de la caché en KB, para mostrarlo en la UI. */
    fun cacheSizeKb(): Long =
        Shell.cmd("du -sk ${ModulePaths.BASE}/cache 2>/dev/null | cut -f1").exec().out
            .firstOrNull()?.trim()?.toLongOrNull() ?: 0L

    /**
     * Borra la caché en disco (scripts/clear_cache.sh). Solo tiene efecto
     * con el bind desmontado: si sigue montado, el script se niega para no
     * perder escrituras pendientes y Result.output empieza con "ERROR".
     */
    fun clearCache(): Result = run("sh ${ModulePaths.SCRIPTS}/clear_cache.sh")

    /** Subcarpetas (sin ocultas) de [path], ordenadas. Se lista con root para no depender de permisos de almacenamiento. */
    fun listDirs(path: String): List<String> =
        Shell.cmd("ls -1p ${sq(path)} 2>/dev/null").exec().out
            .filter { it.endsWith("/") }
            .map { it.removeSuffix("/") }
            .filter { it.isNotEmpty() && !it.startsWith(".") }
            .sortedBy { it.lowercase() }

    fun makeDir(path: String): Result = run("mkdir -p ${sq(path)}")

    data class Result(val success: Boolean, val output: String)
}
