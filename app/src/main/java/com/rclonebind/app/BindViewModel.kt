package com.rclonebind.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.os.SystemClock
import com.rclonebind.app.root.DEFAULT_TARGET_PATH
import com.rclonebind.app.root.DriveAuthParser
import com.rclonebind.app.root.DriveAuthState
import com.rclonebind.app.root.DriveOptions
import com.rclonebind.app.root.PerfMode
import com.rclonebind.app.root.RemoteProfile
import com.rclonebind.app.root.RootShell
import com.rclonebind.app.root.cleanHost
import com.rclonebind.app.root.cleanTargetPath
import com.rclonebind.app.root.validateTargetPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class Snapshot(
    val profiles: List<RemoteProfile>,
    val active: String?,
    val status: String,
    val autostart: Boolean,
    val targetPath: String,
    val perfMode: PerfMode,
    val cacheGb: Int?
)

class BindViewModel : ViewModel() {

    var isMounted by mutableStateOf(false)
        private set
    /** Servidor que está montado ahora mismo (null si no hay o no se sabe). */
    var mountedRemote by mutableStateOf<String?>(null)
        private set
    var autostart by mutableStateOf(false)
        private set
    var logs by mutableStateOf("")
        private set
    var rootGranted by mutableStateOf<Boolean?>(null)
        private set
    var profiles by mutableStateOf<List<RemoteProfile>>(emptyList())
        private set
    /** Ruta donde queda visible el bind (editable desde Inicio). */
    var targetPath by mutableStateOf(DEFAULT_TARGET_PATH)
        private set
    /** Perfil de rendimiento del montaje (Equilibrado o Máximo). */
    var perfMode by mutableStateOf(PerfMode.BALANCED)
        private set
    /** Tamaño de caché elegido en GB; null = el que trae el perfil. */
    var cacheGb by mutableStateOf<Int?>(null)
        private set
    /** Servidor seleccionado: el que usa el botón Montar. */
    var activeName by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    /** true mientras dura un "deslizar para actualizar" (lo muestra el indicador). */
    var refreshing by mutableStateOf(false)
        private set
    /** Progreso del inicio de sesión con Google (lo muestra el formulario de servidor). */
    var driveAuth by mutableStateOf<DriveAuthState>(DriveAuthState.Idle)
        private set
    /** Mensaje de una sola vez; la UI lo muestra en un snackbar y lo consume. */
    var message by mutableStateOf<String?>(null)
        private set

    fun consumeMessage() {
        message = null
    }

    fun setRootGranted(granted: Boolean) {
        rootGranted = granted
    }

    fun refreshAll() = viewModelScope.launch { reload() }

    /**
     * Actualiza estado, servidores y log de una vez (gesto de deslizar hacia
     * abajo). El indicador se mantiene un mínimo para que no parpadee cuando
     * la lectura es instantánea.
     */
    fun pullRefresh() {
        if (refreshing) return
        viewModelScope.launch {
            refreshing = true
            try {
                val started = SystemClock.elapsedRealtime()
                reload()
                logs = withContext(Dispatchers.IO) { RootShell.tailLog() }.output
                val remaining = MIN_REFRESH_MS - (SystemClock.elapsedRealtime() - started)
                if (remaining > 0) delay(remaining)
            } finally {
                refreshing = false
            }
        }
    }

    private suspend fun reload() {
        val snap = withContext(Dispatchers.IO) {
            Snapshot(
                profiles = RootShell.loadProfiles(),
                active = RootShell.readActive(),
                status = RootShell.status().output,
                autostart = RootShell.readAutostart(),
                targetPath = RootShell.readTargetPath(),
                perfMode = RootShell.readPerfMode(),
                cacheGb = RootShell.readCacheGb()
            )
        }

        // Si el seleccionado no existe (borrado, o primera vez), se elige el primero.
        var active = snap.active
        if (snap.profiles.isEmpty()) {
            active = null
        } else if (snap.profiles.none { it.name == active }) {
            val first = snap.profiles.first().name
            active = first
            withContext(Dispatchers.IO) { RootShell.setActive(first) }
        }

        profiles = snap.profiles
        activeName = active
        autostart = snap.autostart
        targetPath = snap.targetPath
        perfMode = snap.perfMode
        cacheGb = snap.cacheGb
        isMounted = snap.status.contains("\"mounted\":true")
        mountedRemote = if (isMounted) {
            Regex("\"remote\":\"([^\"]*)\"").find(snap.status)?.groupValues?.get(1)
        } else null
    }

    fun selectProfile(name: String) = viewModelScope.launch {
        activeName = name
        withContext(Dispatchers.IO) { RootShell.setActive(name) }
    }

    fun saveProfile(
        original: String?,
        name: String,
        host: String,
        port: String,
        user: String,
        pass: String
    ) = viewModelScope.launch {
        val cleanName = name.trim()
        val wasActive = original != null && original == activeName
        val result = withContext(Dispatchers.IO) {
            val r = RootShell.saveProfile(original, cleanName, cleanHost(host), port.trim(), user.trim(), pass)
            // Un servidor nuevo queda seleccionado; uno renombrado conserva la selección.
            if (r.success && (original == null || wasActive)) RootShell.setActive(cleanName)
            r
        }
        message = if (result.success) "Servidor guardado" else "Error al guardar: ${result.output.take(200)}"
        reload()
    }

    fun saveDriveProfile(
        original: String?,
        name: String,
        token: String?,
        options: DriveOptions
    ) = viewModelScope.launch {
        val cleanName = name.trim()
        val wasActive = original != null && original == activeName
        val result = withContext(Dispatchers.IO) {
            val r = RootShell.saveDriveProfile(original, cleanName, token, options)
            if (r.success && (original == null || wasActive)) RootShell.setActive(cleanName)
            r
        }
        if (!result.success) {
            message = "Error al guardar: ${result.output.take(200)}"
            reload()
            return@launch
        }
        message = "Servidor guardado"
        reload()

        // Comprobación real contra Google (sesión, red, DNS, certificados):
        // así un fallo se ve ahora y no recién al intentar montar.
        val check = withContext(Dispatchers.IO) { RootShell.checkRemote(cleanName) }
        message = if (check.success) {
            "Google Drive conectado"
        } else {
            val detail = check.output.lines().lastOrNull { it.isNotBlank() }?.take(200)
                ?: "sin respuesta (sin red o tiempo agotado)"
            "Guardado, pero no se pudo conectar: $detail"
        }
    }

    private var authJob: Job? = null

    /**
     * Inicia el login con Google: lanza `rclone authorize` con root, espera la
     * URL (el formulario la abre en el navegador) y luego el token. Si el
     * usuario cancela o cierra el formulario, [cancelDriveLogin] detiene todo.
     */
    fun startDriveLogin(clientId: String, clientSecret: String) {
        authJob?.cancel()
        authJob = viewModelScope.launch {
            driveAuth = DriveAuthState.Starting
            try {
                val started = withContext(Dispatchers.IO) { RootShell.driveAuthStart(clientId, clientSecret) }
                if (!started.success) {
                    driveAuth = DriveAuthState.Failed("No se pudo iniciar rclone: ${started.output.takeLast(200)}")
                    return@launch
                }
                val deadline = SystemClock.elapsedRealtime() + AUTH_TIMEOUT_MS
                while (isActive) {
                    delay(AUTH_POLL_MS)
                    val output = withContext(Dispatchers.IO) { RootShell.driveAuthOutput() }
                    val progress = DriveAuthParser.parse(output)

                    val token = progress.token
                    if (token != null) {
                        driveAuth = DriveAuthState.Success(token)
                        return@launch
                    }
                    if (progress.exitCode != null) {
                        driveAuth = DriveAuthState.Failed(progress.error ?: "El inicio de sesión terminó sin resultado")
                        return@launch
                    }
                    val url = progress.url
                    if (url != null && driveAuth !is DriveAuthState.WaitingBrowser) {
                        driveAuth = DriveAuthState.WaitingBrowser(url)
                    }
                    if (SystemClock.elapsedRealtime() > deadline) {
                        driveAuth = DriveAuthState.Failed("Tiempo agotado esperando la autorización")
                        return@launch
                    }
                }
            } finally {
                // Siempre: mata rclone si sigue vivo y borra auth.out (lleva el token).
                withContext(NonCancellable + Dispatchers.IO) { RootShell.driveAuthStop() }
            }
        }
    }

    fun cancelDriveLogin() {
        authJob?.cancel()
        authJob = null
        driveAuth = DriveAuthState.Idle
    }

    override fun onCleared() {
        authJob?.cancel()
        super.onCleared()
    }

    fun deleteProfile(name: String) = viewModelScope.launch {
        val result = withContext(Dispatchers.IO) { RootShell.deleteProfile(name) }
        message = if (result.success) "Servidor eliminado" else "Error al eliminar: ${result.output.take(200)}"
        reload()
    }

    fun toggleMount() = viewModelScope.launch {
        if (busy) return@launch
        val target = activeName
        if (target == null && !isMounted) {
            message = "Agrega un servidor primero"
            return@launch
        }
        val mounted = isMounted
        val current = mountedRemote
        busy = true
        val result = withContext(Dispatchers.IO) {
            when {
                mounted && (current == null || current == target) -> RootShell.unmount()
                mounted -> {
                    // Hay otro servidor montado: se desmonta y se monta el seleccionado.
                    RootShell.unmount()
                    RootShell.mount()
                }
                else -> RootShell.mount()
            }
        }
        message = if (result.success) null else "Error: ${result.output.takeLast(200)}"
        reload()
        busy = false
    }

    fun setTargetPath(path: String) = viewModelScope.launch {
        val clean = cleanTargetPath(path)
        val error = validateTargetPath(clean)
        if (error != null) {
            message = error
            return@launch
        }
        val result = withContext(Dispatchers.IO) { RootShell.setTargetPath(clean) }
        if (result.success) {
            targetPath = clean
            message = if (isMounted) "Ruta guardada. Vuelve a montar para aplicarla." else "Ruta guardada"
        } else {
            message = "Error al guardar la ruta: ${result.output.take(200)}"
        }
    }

    fun setPerfMode(mode: PerfMode) = viewModelScope.launch {
        perfMode = mode
        val result = withContext(Dispatchers.IO) { RootShell.setPerfMode(mode) }
        message = when {
            !result.success -> "Error al guardar: ${result.output.take(200)}"
            isMounted -> "Guardado. Vuelve a montar para aplicarlo."
            else -> null
        }
    }

    /** [gb] null restablece el tamaño del perfil. */
    fun setCacheGb(gb: Int?) = viewModelScope.launch {
        cacheGb = gb
        val result = withContext(Dispatchers.IO) { RootShell.setCacheGb(gb) }
        message = when {
            !result.success -> "Error al guardar: ${result.output.take(200)}"
            isMounted -> "Guardado. Vuelve a montar para aplicarlo."
            else -> null
        }
    }

    fun setAutostart(enabled: Boolean) = viewModelScope.launch {
        autostart = enabled
        withContext(Dispatchers.IO) { RootShell.setAutostart(enabled) }
    }

    fun refreshLogs() = viewModelScope.launch {
        val result = withContext(Dispatchers.IO) { RootShell.tailLog() }
        logs = result.output
    }

    /** Borra el log: la pantalla se vacía al instante y el archivo se trunca en segundo plano. */
    fun clearLogs() {
        logs = ""
        viewModelScope.launch { withContext(Dispatchers.IO) { RootShell.clearLog() } }
    }

    private companion object {
        const val AUTH_POLL_MS = 600L
        const val MIN_REFRESH_MS = 500L
        // El script corta a los 300 s; esto es solo la red de seguridad de la app.
        const val AUTH_TIMEOUT_MS = 330_000L
    }
}
