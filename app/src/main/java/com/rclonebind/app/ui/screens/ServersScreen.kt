package com.rclonebind.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rclonebind.app.BindViewModel
import com.rclonebind.app.root.RemoteProfile
import com.rclonebind.app.ui.components.LocalContentBottomInset
import com.rclonebind.app.ui.components.ScreenContainer
import com.rclonebind.app.ui.components.ServerCardStack
import com.rclonebind.app.ui.components.ServerSheet

@Composable
fun ServersScreen(vm: BindViewModel) {
    LaunchedEffect(Unit) { vm.refreshAll() }

    var showSheet by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<RemoteProfile?>(null) }
    var deleteTarget by remember { mutableStateOf<RemoteProfile?>(null) }

    val openNew = {
        editTarget = null
        showSheet = true
    }

    Box(Modifier.fillMaxSize()) {
        ScreenContainer(
            title = "Servidores",
            actions = {
                IconButton(onClick = openNew) {
                    Icon(Icons.Default.Add, contentDescription = "Agregar servidor")
                }
            }
        ) {
            if (vm.profiles.isEmpty()) {
                Text(
                    "Todavía no hay servidores guardados. Agrega un servidor FTP o tu Google Drive para montarlo como carpeta en tu almacenamiento.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FilledTonalButton(onClick = openNew) { Text("Agregar servidor") }
            } else {
                Text(
                    "Toca una tarjeta para elegir el servidor que se monta.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ServerCardStack(
                    profiles = vm.profiles,
                    selected = vm.activeName,
                    onSelect = { vm.selectProfile(it) },
                    onEdit = { p ->
                        editTarget = p
                        showSheet = true
                    },
                    onDelete = { deleteTarget = it }
                )
            }
        }
    }

    if (showSheet) {
        ServerSheet(
            initial = editTarget,
            existingNames = vm.profiles.map { it.name },
            driveAuth = vm.driveAuth,
            onDriveLogin = { clientId, clientSecret -> vm.startDriveLogin(clientId, clientSecret) },
            onDriveCancel = { vm.cancelDriveLogin() },
            onSaveFtp = { name, host, port, user, pass ->
                vm.saveProfile(editTarget?.name, name, host, port, user, pass)
                vm.cancelDriveLogin()
                showSheet = false
            },
            onSaveDrive = { name, token, options ->
                vm.saveDriveProfile(editTarget?.name, name, token, options)
                vm.cancelDriveLogin()
                showSheet = false
            },
            onDismiss = {
                // Cerrar el formulario a mitad del login corta rclone y borra el token temporal.
                vm.cancelDriveLogin()
                showSheet = false
            }
        )
    }

    deleteTarget?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Eliminar servidor") },
            text = { Text("Se borran los datos guardados de «${profile.name}». No se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteProfile(profile.name)
                    deleteTarget = null
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancelar") }
            }
        )
    }
}
