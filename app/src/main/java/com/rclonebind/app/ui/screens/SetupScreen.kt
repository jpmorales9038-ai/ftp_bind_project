package com.rclonebind.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rclonebind.app.BindViewModel

@Composable
fun SetupScreen(vm: BindViewModel) {
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("21") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Configurar servidor FTP", style = MaterialTheme.typography.titleLarge)

        OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("Host / IP") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text("Puerto") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = user, onValueChange = { user = it }, label = { Text("Usuario") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            value = pass,
            onValueChange = { pass = it },
            label = { Text("Contraseña") },
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = { vm.saveFtpConfig(host, port, user, pass) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Guardar y probar")
        }

        vm.lastMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}
