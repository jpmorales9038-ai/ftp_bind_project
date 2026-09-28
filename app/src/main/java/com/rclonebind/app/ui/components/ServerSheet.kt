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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.rclonebind.app.root.FtpProfile
import com.rclonebind.app.root.cleanHost
import com.rclonebind.app.root.validateProfileName

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
