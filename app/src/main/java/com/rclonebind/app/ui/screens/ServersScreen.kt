package com.rclonebind.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
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
import com.rclonebind.app.root.RemoteType
import com.rclonebind.app.ui.components.DualPaneContentWidth
import com.rclonebind.app.ui.components.ScreenContainer
import com.rclonebind.app.ui.components.ServerCardStack
import com.rclonebind.app.ui.components.ServerSheet
import com.rclonebind.app.ui.components.rememberIsDualPane

@Composable
fun ServersScreen(vm: BindViewModel) {
    LaunchedEffect(Unit) { vm.refreshAll() }

    var showSheet by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<RemoteProfile?>(null) }
    var newProfileType by remember { mutableStateOf(RemoteType.FTP) }
    var deleteTarget by remember { mutableStateOf<RemoteProfile?>(null) }

    fun openNew(type: RemoteType = RemoteType.FTP) {
        editTarget = null
        newProfileType = type
        showSheet = true
    }

    val dualPane = rememberIsDualPane()

    Box(Modifier.fillMaxSize()) {
        ScreenContainer(
            title = "Servidores",
            refreshing = vm.refreshing,
            onRefresh = { vm.pullRefresh() },
            maxContentWidth = if (dualPane) DualPaneContentWidth else null,
            actions = {
                IconButton(onClick = { openNew() }) {
                    Icon(Icons.Default.Add, contentDescription = "Agregar servidor")
                }
            }
        ) {
            if (dualPane) {
                val ftp = vm.profiles.filter { it.type == RemoteType.FTP }
                val drive = vm.profiles.filter { it.type == RemoteType.DRIVE }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    ServerPanel(
                        modifier = Modifier.weight(1f),
                        type = RemoteType.FTP,
                        emptyHint = "Agrega un servidor FTP para montarlo como carpeta.",
                        profiles = ftp,
                        selected = vm.activeName,
                        onSelect = { vm.selectProfile(it) },
                        onEdit = { p -> editTarget = p; showSheet = true },
                        onDelete = { deleteTarget = it }
                    )
                    ServerPanel(
                        modifier = Modifier.weight(1f),
                        type = RemoteType.DRIVE,
                        emptyHint = "Conecta tu cuenta de Google Drive para montarla como carpeta.",
                        profiles = drive,
                        selected = vm.activeName,
                        onSelect = { vm.selectProfile(it) },
                        onEdit = { p -> editTarget = p; showSheet = true },
                        onDelete = { deleteTarget = it }
                    )
                }
            } else if (vm.profiles.isEmpty()) {
                Text(
                    "Todavía no hay servidores guardados. Agrega un servidor FTP o tu Google Drive para montarlo como carpeta en tu almacenamiento.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FilledTonalButton(onClick = { openNew() }) { Text("Agregar servidor") }
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
            initialType = newProfileType,
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

/**
 * Una columna del doble panel: título del tipo de remoto con su propio
 * botón de agregar, y debajo su stack (o un aviso si todavía no tiene
 * ningún servidor guardado). Solo se usa en pantallas anchas; en vertical
 * los servidores de ambos tipos se ven mezclados en un único stack.
 */
@Composable
private fun ServerPanel(
    type: RemoteType,
    emptyHint: String,
    profiles: List<RemoteProfile>,
    selected: String?,
    onSelect: (String) -> Unit,
    onEdit: (RemoteProfile) -> Unit,
    onDelete: (RemoteProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Text(type.label, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        if (profiles.isEmpty()) {
            Text(
                emptyHint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            ServerCardStack(
                profiles = profiles,
                selected = selected,
                onSelect = onSelect,
                onEdit = onEdit,
                onDelete = onDelete
            )
        }
    }
}
