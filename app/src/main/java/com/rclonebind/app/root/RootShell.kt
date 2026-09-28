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
    const val STATUS_FILE = "$BASE/status.json"
    const val LOG_FILE = "$BASE/mount.log"
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

    fun mount(): Result = run("sh ${ModulePaths.SCRIPTS}/mount.sh")

    fun unmount(): Result = run("sh ${ModulePaths.SCRIPTS}/unmount.sh")

    fun status(): Result = run("cat ${ModulePaths.STATUS_FILE} 2>/dev/null || echo '{\"mounted\":false}'")

    fun tailLog(lines: Int = 200): Result = run("tail -n $lines ${ModulePaths.LOG_FILE} 2>/dev/null")

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

    fun loadProfiles(): List<FtpProfile> = readConf().toProfiles()

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

        if (pass.isNotEmpty()) {
            // rclone espera la contraseña "ofuscada" (reversible, no es
            // cifrado): en texto plano falla con "input too short when
            // revealing password". Por eso pasa por "rclone obscure".
            val obscure = Shell.cmd("${ModulePaths.BIN} obscure ${sq(pass)}").exec()
            if (!obscure.isSuccess) {
                return Result(false, "No se pudo ofuscar la contraseña: " + obscure.out.joinToString("\n"))
            }
            val value = obscure.out.joinToString("").trim()
            if (value.isEmpty()) return Result(false, "rclone obscure devolvió un valor vacío")
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
        return writeConf(out)
    }

    fun deleteProfile(name: String): Result {
        val conf = readConf()
        conf.remove(name)
        return writeConf(conf)
    }

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

    fun readAutostart(): Boolean =
        Shell.cmd("cat ${ModulePaths.CONFIG_DIR}/autostart 2>/dev/null").exec().out
            .joinToString("").trim() == "1"

    fun setAutostart(enabled: Boolean): Result =
        run("mkdir -p ${ModulePaths.CONFIG_DIR} && echo '${if (enabled) "1" else "0"}' > ${ModulePaths.CONFIG_DIR}/autostart")

    data class Result(val success: Boolean, val output: String)
}
