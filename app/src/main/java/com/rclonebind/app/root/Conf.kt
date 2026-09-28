package com.rclonebind.app.root

/** Tipos de remoto que la app sabe crear (el valor de "type" en rclone.conf). */
enum class RemoteType(val rclone: String, val label: String) {
    FTP("ftp", "FTP"),
    DRIVE("drive", "Google Drive")
}

/** Perfil de rendimiento del montaje (lo lee scripts/mount.sh desde config/perf). */
enum class PerfMode(val id: String, val label: String) {
    BALANCED("balanced", "Equilibrado"),
    MAX("max", "Máximo")
}

/**
 * Rango del tamaño de caché en GB que ofrece la app. El tope real de lo que
 * cabe en el teléfono no es este número: es el espacio libre real, que
 * mount.sh siempre respeta dejando 2 GB de margen (--vfs-cache-min-free-space)
 * y que "Probar rendimiento" avisa si no alcanza. 200 GB da lugar a juegos
 * pesados completos sin obligar a bajar el perfil de rendimiento por un tope
 * artificial de la app.
 */
const val CACHE_GB_MIN = 1
const val CACHE_GB_MAX = 200

/** Tamaño de caché que usa mount.sh cuando el usuario no eligió uno (debe coincidir con el script). */
fun defaultCacheGb(mode: PerfMode): Int = if (mode == PerfMode.MAX) 10 else 1

/** KB a un texto legible ("340 MB", "2.3 GB"), para mostrar el tamaño de la caché en disco. */
fun formatCacheKb(kb: Long): String = when {
    kb >= 1_048_576 -> "%.1f GB".format(kb / 1_048_576.0)
    kb >= 1024 -> "%.0f MB".format(kb / 1024.0)
    else -> "$kb KB"
}

/** Ajustes propios de un remoto Google Drive. El token nunca sale del rclone.conf. */
data class DriveOptions(
    val clientId: String = "",
    val clientSecret: String = "",
    val readOnly: Boolean = false,
    val rootFolderId: String = "",
    val teamDrive: String = "",
    /** Equivale a --drive-acknowledge-abuse: permite bajar archivos que Google marca como malware/spam. */
    val acknowledgeAbuse: Boolean = false,
    val hasToken: Boolean = false
)

/**
 * Un servidor guardado (FTP o Google Drive). Los campos host/port/user/
 * hasPassword solo aplican a FTP; [drive] solo a Google Drive. Las
 * contraseñas y el token de sesión nunca salen del rclone.conf.
 */
data class RemoteProfile(
    val name: String,
    val type: RemoteType,
    val host: String = "",
    val port: String = "21",
    val user: String = "",
    val hasPassword: Boolean = false,
    val drive: DriveOptions? = null
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

fun Conf.toProfiles(): List<RemoteProfile> =
    entries.mapNotNull { entry ->
        val v = entry.value
        when (v["type"]) {
            RemoteType.FTP.rclone -> RemoteProfile(
                name = entry.key,
                type = RemoteType.FTP,
                host = v["host"].orEmpty(),
                port = v["port"] ?: "21",
                user = v["user"].orEmpty(),
                hasPassword = !v["pass"].isNullOrEmpty()
            )
            RemoteType.DRIVE.rclone -> RemoteProfile(
                name = entry.key,
                type = RemoteType.DRIVE,
                drive = DriveOptions(
                    clientId = v["client_id"].orEmpty(),
                    clientSecret = v["client_secret"].orEmpty(),
                    readOnly = v["scope"] == DRIVE_SCOPE_READONLY,
                    rootFolderId = v["root_folder_id"].orEmpty(),
                    teamDrive = v["team_drive"].orEmpty(),
                    acknowledgeAbuse = v["acknowledge_abuse"] == "true",
                    hasToken = !v["token"].isNullOrEmpty()
                )
            )
            // Otros tipos (sftp, s3...) que el usuario haya puesto a mano se
            // conservan en el archivo pero no se muestran en la app.
            else -> null
        }
    }

const val DRIVE_SCOPE_FULL = "drive"
const val DRIVE_SCOPE_READONLY = "drive.readonly"

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

// Patrones de link para compartir una carpeta de Drive:
//   https://drive.google.com/drive/folders/<id>?usp=sharing
//   https://drive.google.com/drive/u/0/folders/<id>
//   https://drive.google.com/open?id=<id>                 (link viejo, cualquier archivo/carpeta)
private val DRIVE_FOLDER_URL = Regex("""/folders/([A-Za-z0-9_-]+)""")
private val DRIVE_ID_PARAM = Regex("""[?&]id=([A-Za-z0-9_-]+)""")

/**
 * Es más fácil pegar el link para compartir la carpeta que buscar su ID a
 * mano. Si [raw] matchea alguno de los links de arriba, devuelve solo el
 * ID; si no matchea nada (ya es un ID, o cualquier otro texto), se
 * devuelve tal cual recortado, para no interferir con lo que el usuario
 * esté escribiendo.
 */
fun extractDriveFolderId(raw: String): String {
    val trimmed = raw.trim()
    DRIVE_FOLDER_URL.find(trimmed)?.let { return it.groupValues[1] }
    DRIVE_ID_PARAM.find(trimmed)?.let { return it.groupValues[1] }
    return trimmed
}

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

/** Raíz del almacenamiento interno desde donde se elige la carpeta de destino. */
const val STORAGE_ROOT = "/sdcard"

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
