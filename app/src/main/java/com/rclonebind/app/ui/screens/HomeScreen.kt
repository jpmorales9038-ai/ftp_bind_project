package com.rclonebind.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rclonebind.app.BindViewModel

@Composable
fun HomeScreen(vm: BindViewModel) {
    LaunchedEffect(Unit) { vm.refreshStatus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Estado del bind", style = MaterialTheme.typography.titleLarge)

        AssistChip(
            onClick = { vm.refreshStatus() },
            label = { Text(if (vm.isMounted) "● Montado" else "○ Desmontado") }
        )

        Button(onClick = { vm.toggleMount() }, modifier = Modifier.fillMaxWidth()) {
            Text(if (vm.isMounted) "Desmontar" else "Montar")
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Montar al iniciar")
            Switch(checked = vm.autostart, onCheckedChange = { vm.setAutostart(it) })
        }

        vm.lastMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}
