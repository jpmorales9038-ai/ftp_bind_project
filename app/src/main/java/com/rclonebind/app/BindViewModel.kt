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
        val conf = """
            [remote]
            type = ftp
            host = $host
            port = $port
            user = $user
            pass = $pass
        """.trimIndent()
        val result = withContext(Dispatchers.IO) { RootShell.saveConfig(conf) }
        lastMessage = if (result.success) "Configuración guardada" else "Error al guardar: ${result.output.take(200)}"
    }

    fun refreshLogs() = viewModelScope.launch {
        val result = withContext(Dispatchers.IO) { RootShell.tailLog() }
        logs = result.output
    }
}
