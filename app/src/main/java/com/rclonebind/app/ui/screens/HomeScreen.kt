package com.rclonebind.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rclonebind.app.BindViewModel
import com.rclonebind.app.ui.components.ScreenContainer
import com.rclonebind.app.ui.theme.AppMotion

@Composable
fun HomeScreen(vm: BindViewModel, onOpenServers: () -> Unit) {
    LaunchedEffect(Unit) { vm.refreshAll() }

    val scheme = MaterialTheme.colorScheme
    val mounted = vm.isMounted
    val active = vm.activeName
    val heroColor by animateColorAsState(
        if (mounted) scheme.primaryContainer else scheme.surfaceContainerHigh,
        AppMotion.effects(), label = "heroColor"
    )
    val heroContent by animateColorAsState(
        if (mounted) scheme.onPrimaryContainer else scheme.onSurface,
        AppMotion.effects(), label = "heroContent"
    )

    ScreenContainer {
        Surface(
            color = heroColor,
            contentColor = heroContent,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (mounted) "Montado" else "Desmontado",
                    style = MaterialTheme.typography.headlineLarge
                )
                Text(
                    if (mounted) {
                        "Los archivos de ${vm.mountedRemote ?: "tu servidor"} están en /sdcard/FTP."
                    } else {
                        "Elige un servidor y móntalo en /sdcard/FTP."
                    },
                    style = MaterialTheme.typography.bodyLarge
                )
                if (vm.busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            }
        }

        if (vm.rootGranted == false) {
            Surface(
                color = scheme.errorContainer,
                contentColor = scheme.onErrorContainer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "No se detectó acceso root. Concede el permiso a esta app desde KernelSU Manager.",
                    modifier = Modifier.padding(20.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Surface(
            color = scheme.surfaceContainer,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val selected = vm.profiles.firstOrNull { it.name == active }
                Column(Modifier.weight(1f)) {
                    Text(
                        "Servidor seleccionado",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                    Text(
                        selected?.name ?: "Ninguno",
                        style = MaterialTheme.typography.titleLarge
                    )
                    if (selected != null) {
                        Text(
                            if (selected.user.isEmpty()) selected.host else "${selected.user}@${selected.host}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant
                        )
                    }
                }
                TextButton(onClick = onOpenServers) {
                    Text(if (selected == null) "Agregar" else "Cambiar")
                }
            }
        }

        val label = when {
            vm.busy -> "Trabajando…"
            mounted && (vm.mountedRemote == null || vm.mountedRemote == active) -> "Desmontar"
            mounted -> "Cambiar a $active"
            else -> "Montar"
        }
        Button(
            onClick = { vm.toggleMount() },
            enabled = !vm.busy && (active != null || mounted),
            colors = if (mounted) ButtonDefaults.filledTonalButtonColors() else ButtonDefaults.buttonColors(),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().height(64.dp)
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium)
        }

        Surface(
            color = scheme.surfaceContainer,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Montar al iniciar", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Monta el servidor seleccionado cuando arranca el teléfono.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant
                    )
                }
                Switch(checked = vm.autostart, onCheckedChange = { vm.setAutostart(it) })
            }
        }
    }
}
