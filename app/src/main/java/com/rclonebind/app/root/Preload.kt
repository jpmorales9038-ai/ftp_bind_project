package com.rclonebind.app.root

/**
 * Progreso de scripts/preload.sh, leído de preload_status.json. Ese archivo
 * solo existe mientras tiene sentido mostrar algo (el perfil activo cachea
 * lecturas completas y hay un montaje con archivos); si no, [parse] devuelve
 * null y la tarjeta de precarga en Inicio no se muestra.
 */
data class PreloadStatus(
    val running: Boolean,
    val remote: String,
    val totalFiles: Int,
    val selectedFiles: Int,
    val selectedMb: Int,
    val doneFiles: Int,
    val doneMb: Int
) {
    /** Ya se cubrió todo lo que entraba en el presupuesto (puede haber quedado fuera por espacio). */
    val finished: Boolean get() = !running && selectedFiles > 0 && doneFiles >= selectedFiles
    /** 0f..1f; 1f si no hay nada que precargar (para no mostrar una barra vacía como "atascada"). */
    val fraction: Float get() = if (selectedMb <= 0) 1f else (doneMb.toFloat() / selectedMb).coerceIn(0f, 1f)
}

object PreloadStatusParser {
    private fun intField(json: String, key: String): Int? =
        Regex("\"$key\":(-?\\d+)").find(json)?.groupValues?.get(1)?.toIntOrNull()

    private fun boolField(json: String, key: String): Boolean? =
        Regex("\"$key\":(true|false)").find(json)?.groupValues?.get(1)?.toBooleanStrictOrNull()

    private fun stringField(json: String, key: String): String =
        Regex("\"$key\":\"([^\"]*)\"").find(json)?.groupValues?.get(1).orEmpty()

    /** null cuando el archivo no existe o está vacío: no hay nada que mostrar. */
    fun parse(json: String): PreloadStatus? {
        if (json.isBlank()) return null
        val running = boolField(json, "running") ?: return null
        return PreloadStatus(
            running = running,
            remote = stringField(json, "remote"),
            totalFiles = intField(json, "total_files") ?: 0,
            selectedFiles = intField(json, "selected_files") ?: 0,
            selectedMb = intField(json, "selected_mb") ?: 0,
            doneFiles = intField(json, "done_files") ?: 0,
            doneMb = intField(json, "done_mb") ?: 0
        )
    }
}
