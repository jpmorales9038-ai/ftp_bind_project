package com.rclonebind.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rclonebind.app.BindViewModel
import com.rclonebind.app.BuildConfig
import com.rclonebind.app.root.RootShell
import com.rclonebind.app.ui.components.AppIcons
import com.rclonebind.app.ui.components.ScreenContainer
import com.rclonebind.app.ui.components.SectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val REPO_URL = "https://github.com/jpmorales9038-ai/ftp_bind_project"
private const val RCLONE_URL = "https://rclone.org"

@Composable
fun AboutScreen(vm: BindViewModel) {
    val scheme = MaterialTheme.colorScheme
    val uriHandler = LocalUriHandler.current

    // La versión de rclone se pide con root; si no hay root queda "—".
    var rcloneVersion by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(vm.rootGranted) {
        if (vm.rootGranted == true) {
            rcloneVersion = withContext(Dispatchers.IO) { RootShell.rcloneVersion() }
        }
    }

    ScreenContainer(title = "Acerca de") {
        // Cabecera: logo + nombre + versión.
        Surface(
            color = scheme.primaryContainer,
            contentColor = scheme.onPrimaryContainer,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(28.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(30.dp))
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF1594A8), Color(0xFF0A4657)))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(AppIcons.Logo, contentDescription = null, modifier = Modifier.size(64.dp))
                }
                Text("RClone FTP Bind", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Text(
                    "Versión ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        SectionCard(
            title = "Qué hace",
            icon = Icons.Default.Info,
            subtitle = "Monta un servidor FTP o Google Drive con rclone y lo muestra como una carpeta " +
                "más de tu almacenamiento interno, para que cualquier app pueda usarlo."
        ) {
            Text(
                "1. Agrega un servidor en la pestaña Servidores.\n" +
                    "2. Elige la carpeta de destino en Inicio.\n" +
                    "3. Toca Montar: los archivos remotos aparecen ahí.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant
            )
        }

        SectionCard(title = "Sistema", icon = Icons.Default.Build) {
            InfoRow("Acceso root", when (vm.rootGranted) {
                true -> "Concedido"
                false -> "No disponible"
                null -> "Comprobando…"
            })
            InfoRow("rclone", rcloneVersion ?: "—")
            InfoRow("Interfaz", "Jetpack Compose · Material 3 Expressive")
        }

        SectionCard(title = "Enlaces", icon = Icons.Default.Share) {
            FilledTonalButton(onClick = { uriHandler.openUri(REPO_URL) }, modifier = Modifier.fillMaxWidth()) {
                Text("Código fuente en GitHub")
            }
            OutlinedButton(onClick = { uriHandler.openUri(RCLONE_URL) }, modifier = Modifier.fillMaxWidth()) {
                Text("Sitio de rclone")
            }
        }

        Text(
            "Usa rclone (licencia MIT), libsu (Apache 2.0) y Haze (Apache 2.0).",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.End, modifier = Modifier.padding(start = 16.dp))
    }
}
