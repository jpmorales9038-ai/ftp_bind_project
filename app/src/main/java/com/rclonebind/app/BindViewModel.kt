package com.rclonebind.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rclonebind.app.root.FtpProfile
import com.rclonebind.app.root.RootShell
import com.rclonebind.app.root.cleanHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class Snapshot(
    val profiles: List<FtpProfile>,
    val active: String?,
    val status: String,
    val autostart: Boolean
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
    var profiles by mutableStateOf<List<FtpProfile>>(emptyList())
        private set
    /** Servidor seleccionado: el que usa el botón Montar. */
    var activeName by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
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

    private suspend fun reload() {
        val snap = withContext(Dispatchers.IO) {
            Snapshot(
                profiles = RootShell.loadProfiles(),
                active = RootShell.readActive(),
                status = RootShell.status().output,
                autostart = RootShell.readAutostart()
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

    fun setAutostart(enabled: Boolean) = viewModelScope.launch {
        autostart = enabled
        withContext(Dispatchers.IO) { RootShell.setAutostart(enabled) }
    }

    fun refreshLogs() = viewModelScope.launch {
        val result = withContext(Dispatchers.IO) { RootShell.tailLog() }
        logs = result.output
    }
}
