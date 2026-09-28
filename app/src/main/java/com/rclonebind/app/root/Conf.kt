package com.rclonebind.app.root

/** Un servidor FTP guardado. La contraseña nunca sale del rclone.conf. */
data class FtpProfile(
    val name: String,
    val host: String,
    val port: String,
    val user: String,
    val hasPassword: Boolean
)

/** rclone.conf en memoria: sección -> (clave -> valor), conservando el orden. */
typealias Conf = LinkedHashMap<String, LinkedHashMap<String, String>>

fun parseConf(text: String): Conf {
    val conf = Conf()
    var current: LinkedHashMap<String, String>? = null
    for (raw in text.lines()) {
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) continue
        if (line.startsWith("[") && line.endsWith("]")) {
            val section = LinkedHashMap<String, String>()
            conf[line.substring(1, line.length - 1)] = section
            current = section
        } else {
            val eq = line.indexOf('=')
            val target = current
            if (eq > 0 && target != null) {
                target[line.substring(0, eq).trim()] = line.substring(eq + 1).trim()
            }
        }
    }
    return conf
}

fun serializeConf(conf: Conf): String =
    conf.entries.joinToString("\n\n") { entry ->
        "[${entry.key}]\n" + entry.value.entries.joinToString("\n") { "${it.key} = ${it.value}" }
    } + "\n"

fun Conf.toProfiles(): List<FtpProfile> =
    entries
        .filter { it.value["type"] == "ftp" }
        .map { entry ->
            FtpProfile(
                name = entry.key,
                host = entry.value["host"].orEmpty(),
                port = entry.value["port"] ?: "21",
                user = entry.value["user"].orEmpty(),
                hasPassword = !entry.value["pass"].isNullOrEmpty()
            )
        }

/**
 * Es muy fácil pegar la dirección completa ("ftp://192.168.1.75") en el campo
 * Host, pero rclone espera solo el host/IP: con esquema o puerto de más falla
 * con "too many colons in address". Se limpia antes de guardar.
 */
fun cleanHost(raw: String): String =
    raw.trim()
        .removePrefix("ftp://")
        .removePrefix("ftps://")
        .removePrefix("http://")
        .removePrefix("https://")
        .substringBefore("/")
        .substringBefore(":")

// Reglas de rclone para el nombre de un remoto: solo estos caracteres ASCII,
// sin empezar con "-" ni con espacio.
private val NAME_REGEX = Regex("^[A-Za-z0-9_.+@][A-Za-z0-9_.+@ -]*$")

fun validateProfileName(name: String, original: String?, existing: List<String>): String? = when {
    name.isEmpty() -> "Escribe un nombre"
    !NAME_REGEX.matches(name) -> "Usa letras sin tilde, números, espacios y _ - . + @"
    name != original && existing.contains(name) -> "Ya existe un servidor con ese nombre"
    else -> null
}

/** Ruta de destino del bind cuando el usuario todavía no configuró una propia. */
const val DEFAULT_TARGET_PATH = "/sdcard/FTP"

/** Quita espacios y la barra final (salvo que la ruta sea solo "/"). */
fun cleanTargetPath(raw: String): String {
    val trimmed = raw.trim()
    return if (trimmed.length > 1) trimmed.trimEnd('/') else trimmed
}

/**
 * Reglas mínimas para la ruta de destino: absoluta, sin ".." (evita salirse
 * de donde debería quedar el bind) y distinta de la raíz del sistema.
 */
fun validateTargetPath(path: String): String? = when {
    path.isEmpty() -> "Escribe una ruta"
    !path.startsWith("/") -> "Debe ser una ruta absoluta (empieza con /)"
    path == "/" -> "No uses la raíz del sistema"
    path.contains("..") -> "La ruta no puede contener \"..\""
    else -> null
}
