package com.rclonebind.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rclonebind.app.root.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BindViewModel : ViewModel() {

    var isMounted by mutableStateOf(false)
        private set
    var autostart by mutableStateOf(false)
        private set
    var logs by mutableStateOf("")
        private set
    var lastMessage by mutableStateOf<String?>(null)
        private set
    var rootGranted by mutableStateOf<Boolean?>(null)
        private set

    fun setRootGranted(granted: Boolean) {
        rootGranted = granted
    }

    fun refreshStatus() = viewModelScope.launch {
        val result = withContext(Dispatchers.IO) { RootShell.status() }
        isMounted = result.output.contains("\"mounted\":true")
    }

    fun toggleMount() = viewModelScope.launch {
        val result = withContext(Dispatchers.IO) {
            if (isMounted) RootShell.unmount() else RootShell.mount()
        }
        lastMessage = if (result.success) null else "Error: ${result.output.take(200)}"
        refreshStatus()
    }

    fun setAutostart(enabled: Boolean) = viewModelScope.launch {
        autostart = enabled
        withContext(Dispatchers.IO) { RootShell.setAutostart(enabled) }
    }

    fun saveFtpConfig(host: String, port: String, user: String, pass: String) = viewModelScope.launch {
        // Es muy fácil pegar la dirección completa ("ftp://192.168.1.75") en
        // el campo Host — pero rclone espera ahí solo el host/IP, sin
        // esquema ni ruta, y si le llega con "ftp://" arma una dirección con
        // colones de más ("ftp://192.168.1.75:2121") que el resolver de red
        // rechaza con "too many colons in address". Se limpia acá antes de
        // guardar para que ese error no vuelva a aparecer.
        val cleanHost = host
            .trim()
            .removePrefix("ftp://")
            .removePrefix("ftps://")
            .removePrefix("http://")
            .removePrefix("https://")
            .substringBefore("/")
            .substringBefore(":") // por si el usuario ya incluyó el puerto acá
        val result = withContext(Dispatchers.IO) { RootShell.saveConfig(cleanHost, port, user, pass) }
        lastMessage = if (result.success) "Configuración guardada" else "Error al guardar: ${result.output.take(200)}"
    }

    fun refreshLogs() = viewModelScope.launch {
        val result = withContext(Dispatchers.IO) { RootShell.tailLog() }
        logs = result.output
    }
}
