package com.rclonebind.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.rclonebind.app.BindViewModel

@Composable
fun LogsScreen(vm: BindViewModel) {
    LaunchedEffect(Unit) { vm.refreshLogs() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Logs de rclone", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { vm.refreshLogs() }) { Text("Actualizar") }
        }
        Text(
            text = vm.logs.ifBlank { "Sin logs todavía." },
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp)
        )
    }
}
