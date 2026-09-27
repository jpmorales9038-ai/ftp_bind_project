package com.rclonebind.app.root

import com.topjohnwu.superuser.Shell

/**
 * Rutas del módulo KernelSU. Ajustar <MODULE_ID> al id real definido en module.prop.
 */
object ModulePaths {
    const val MODULE_ID = "rclone_ftp_bind"
    const val BASE = "/data/adb/modules/$MODULE_ID"
    const val SCRIPTS = "$BASE/scripts"
    const val CONFIG_DIR = "$BASE/config"
    const val RCLONE_CONF = "$CONFIG_DIR/rclone.conf"
    const val STATUS_FILE = "$BASE/status.json"
    const val LOG_FILE = "$BASE/mount.log"
}

/**
 * Ejecuta los scripts del módulo vía su. Cada función devuelve stdout+stderr
 * combinados para poder mostrarlos en la pantalla de Logs.
 */
object RootShell {

    init {
        Shell.enableVerboseLogging = false
        Shell.setDefaultBuilder(
            Shell.Builder.create().setFlags(Shell.FLAG_REDIRECT_STDERR)
        )
    }

    private fun run(cmd: String): Result {
        val result = Shell.cmd(cmd).exec()
        return Result(result.isSuccess, result.out.joinToString("\n"))
    }

    fun mount(): Result = run("sh ${ModulePaths.SCRIPTS}/mount.sh")

    fun unmount(): Result = run("sh ${ModulePaths.SCRIPTS}/unmount.sh")

    fun status(): Result = run("cat ${ModulePaths.STATUS_FILE} 2>/dev/null || echo '{\"mounted\":false}'")

    fun tailLog(lines: Int = 200): Result = run("tail -n $lines ${ModulePaths.LOG_FILE} 2>/dev/null")

    fun saveConfig(rcloneConfContent: String): Result {
        val escaped = rcloneConfContent.replace("'", "'\\''")
        return run("mkdir -p ${ModulePaths.CONFIG_DIR} && printf '%s' '$escaped' > ${ModulePaths.RCLONE_CONF}")
    }

    fun setAutostart(enabled: Boolean): Result =
        run("mkdir -p ${ModulePaths.CONFIG_DIR} && echo '${if (enabled) "1" else "0"}' > ${ModulePaths.CONFIG_DIR}/autostart")

    data class Result(val success: Boolean, val output: String)
}
