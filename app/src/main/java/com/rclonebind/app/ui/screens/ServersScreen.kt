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
import com.rclonebind.app.root.FtpProfile
import com.rclonebind.app.ui.components.ScreenContainer
import com.rclonebind.app.ui.components.ServerCardStack
import com.rclonebind.app.ui.components.ServerSheet

@Composable
fun ServersScreen(vm: BindViewModel) {
    LaunchedEffect(Unit) { vm.refreshAll() }

    var showSheet by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<FtpProfile?>(null) }
    var deleteTarget by remember { mutableStateOf<FtpProfile?>(null) }

    val openNew = {
        editTarget = null
        showSheet = true
    }

    Box(Modifier.fillMaxSize()) {
        ScreenContainer {
            Text("Servidores", style = MaterialTheme.typography.headlineLarge)

            if (vm.profiles.isEmpty()) {
                Text(
                    "Todavía no hay servidores guardados. Agrega uno para montarlo como carpeta en tu almacenamiento.",
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
                // Espacio para que el botón flotante no tape la última tarjeta
                Spacer(Modifier.height(72.dp))
            }
        }

        if (vm.profiles.isNotEmpty()) {
            ExtendedFloatingActionButton(
                onClick = openNew,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Agregar") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
            )
        }
    }

    if (showSheet) {
        ServerSheet(
            initial = editTarget,
            existingNames = vm.profiles.map { it.name },
            onSave = { name, host, port, user, pass ->
                vm.saveProfile(editTarget?.name, name, host, port, user, pass)
                showSheet = false
            },
            onDismiss = { showSheet = false }
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
