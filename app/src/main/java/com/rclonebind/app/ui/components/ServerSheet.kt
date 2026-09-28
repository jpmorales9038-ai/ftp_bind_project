package com.rclonebind.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rclonebind.app.net.FTP_SCAN_HOST_COUNT
import com.rclonebind.app.net.FoundFtpServer
import com.rclonebind.app.net.scanForFtpServers
import com.rclonebind.app.root.FtpProfile
import com.rclonebind.app.root.cleanHost
import com.rclonebind.app.root.validateProfileName
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Formulario para agregar un servidor, o editar [initial] si no es null. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSheet(
    initial: FtpProfile?,
    existingNames: List<String>,
    onSave: (name: String, host: String, port: String, user: String, pass: String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var host by remember { mutableStateOf(initial?.host ?: "") }
    var port by remember { mutableStateOf(initial?.port ?: "21") }
    var user by remember { mutableStateOf(initial?.user ?: "") }
    var pass by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf<String?>(null) }
    var hostError by remember { mutableStateOf<String?>(null) }
    var portError by remember { mutableStateOf<String?>(null) }

    // Detector de servidores FTP en la red local (solo llena Host/Puerto;
    // el nombre lo sigue eligiendo el usuario).
    val scope = rememberCoroutineScope()
    var scanJob by remember { mutableStateOf<Job?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var scanChecked by remember { mutableStateOf(0) }
    var scanResults by remember { mutableStateOf<List<FoundFtpServer>>(emptyList()) }
    var scanMessage by remember { mutableStateOf<String?>(null) }

    fun startScan() {
        scanResults = emptyList()
        scanMessage = null
        scanChecked = 0
        scanning = true
        scanJob = scope.launch {
            val found = scanForFtpServers { checked, _ -> scanChecked = checked }
            scanResults = found
            scanMessage = if (found.isEmpty()) "No se encontró ningún servidor FTP en la red" else null
            scanning = false
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (initial == null) "Nuevo servidor" else "Editar servidor",
                style = MaterialTheme.typography.headlineMedium
            )

            OutlinedButton(
                onClick = {
                    if (scanning) {
                        scanJob?.cancel()
                        scanning = false
                    } else {
                        startScan()
                    }
                },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (scanning) "Cancelar búsqueda ($scanChecked/$FTP_SCAN_HOST_COUNT)" else "Buscar servidores FTP en mi red")
            }

            if (scanning) {
                LinearProgressIndicator(
                    progress = { scanChecked / FTP_SCAN_HOST_COUNT.toFloat() },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            scanMessage?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (scanResults.isNotEmpty()) {
                Text("Toca uno para usarlo", style = MaterialTheme.typography.labelLarge)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    scanResults.forEach { server ->
                        Surface(
                            onClick = {
                                host = server.ip
                                hostError = null
                                if (port.isBlank()) port = "21"
                                scanResults = emptyList()
                            },
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(server.ip, style = MaterialTheme.typography.bodyLarge)
                                if (!server.banner.isNullOrBlank()) {
                                    Text(
                                        server.banner,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it; nameError = null },
                label = { Text("Nombre") },
                singleLine = true,
                isError = nameError != null,
                supportingText = nameError?.let { { Text(it) } },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = host,
                onValueChange = { host = it; hostError = null },
                label = { Text("Host o IP") },
                singleLine = true,
                isError = hostError != null,
                supportingText = hostError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = port,
                onValueChange = { port = it; portError = null },
                label = { Text("Puerto") },
                singleLine = true,
                isError = portError != null,
                supportingText = portError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = user,
                onValueChange = { user = it },
                label = { Text("Usuario") },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = pass,
                onValueChange = { pass = it },
                label = { Text("Contraseña") },
                singleLine = true,
                supportingText = if (initial != null) {
                    { Text("Déjala vacía para conservar la actual") }
                } else null,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    val cleanName = name.trim()
                    nameError = validateProfileName(cleanName, initial?.name, existingNames)
                    hostError = if (cleanHost(host).isEmpty()) "Escribe la dirección del servidor" else null
                    val portNumber = port.trim().toIntOrNull()
                    portError = if (portNumber == null || portNumber !in 1..65535) "Usa un puerto entre 1 y 65535" else null
                    if (nameError == null && hostError == null && portError == null) {
                        onSave(cleanName, host, port, user, pass)
                    }
                },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text(if (initial == null) "Guardar servidor" else "Guardar cambios")
            }
        }
    }
}
