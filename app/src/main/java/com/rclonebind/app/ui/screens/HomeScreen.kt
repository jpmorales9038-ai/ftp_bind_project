package com.rclonebind.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.rclonebind.app.ui.components.AppIcons
import com.rclonebind.app.root.CACHE_GB_MAX
import com.rclonebind.app.root.CACHE_GB_MIN
import com.rclonebind.app.root.PerfMode
import com.rclonebind.app.root.defaultCacheGb
import com.rclonebind.app.root.formatCacheKb
import com.rclonebind.app.ui.components.DualPaneContentWidth
import com.rclonebind.app.ui.components.FolderPickerDialog
import com.rclonebind.app.ui.components.OptionTile
import com.rclonebind.app.ui.components.PerfTestSheet
import com.rclonebind.app.ui.components.ScreenContainer
import com.rclonebind.app.ui.components.SectionCard
import com.rclonebind.app.ui.components.rememberIsDualPane
import com.rclonebind.app.ui.theme.AppMotion
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(vm: BindViewModel, onOpenServers: () -> Unit) {
    LaunchedEffect(Unit) { vm.refreshAll() }

    val scheme = MaterialTheme.colorScheme
    val mounted = vm.isMounted
    val active = vm.activeName
    var showPathDialog by remember { mutableStateOf(false) }
    var showRamCacheConfirm by remember { mutableStateOf(false) }
    var showPerfTest by remember { mutableStateOf(false) }
    val heroColor by animateColorAsState(
        if (mounted) scheme.primaryContainer else scheme.surfaceContainerHigh,
        AppMotion.effects(), label = "heroColor"
    )
    val heroContent by animateColorAsState(
        if (mounted) scheme.onPrimaryContainer else scheme.onSurface,
        AppMotion.effects(), label = "heroContent"
    )
    val dualPane = rememberIsDualPane()

    val onRunPerfTest: () -> Unit = {
        showPerfTest = true
        vm.startPerfTest()
    }

    ScreenContainer(
        title = "Inicio",
        refreshing = vm.refreshing,
        onRefresh = { vm.pullRefresh() },
        maxContentWidth = if (dualPane) DualPaneContentWidth else null,
        actions = {
            IconButton(onClick = { vm.refreshAll() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Actualizar")
            }
        }
    ) {
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
                        "Los archivos de ${vm.mountedRemote ?: "tu servidor"} están en ${vm.targetPath}."
                    } else {
                        "Elige un servidor y móntalo en ${vm.targetPath}."
                    },
                    style = MaterialTheme.typography.bodyLarge
                )
                if (vm.busy) {
                    LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
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

        // En apaisado (o tablet) hay ancho de sobra para dos columnas: a la
        // izquierda lo que se usa para montar (servidor, carpeta, botón); a
        // la derecha los ajustes (autostart y rendimiento). En vertical
        // sigue todo apilado en una sola columna, como siempre.
        if (dualPane) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    MountCard(
                        vm = vm,
                        mounted = mounted,
                        active = active,
                        onOpenServers = onOpenServers,
                        onChangeFolder = { showPathDialog = true }
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    AutostartCard(vm)
                    PerfCard(
                        vm = vm,
                        mounted = mounted,
                        onRunPerfTest = onRunPerfTest,
                        onRequestRamCache = { showRamCacheConfirm = true }
                    )
                }
            }
        } else {
            MountCard(
                vm = vm,
                mounted = mounted,
                active = active,
                onOpenServers = onOpenServers,
                onChangeFolder = { showPathDialog = true }
            )
            AutostartCard(vm)
            PerfCard(
                vm = vm,
                mounted = mounted,
                onRunPerfTest = onRunPerfTest,
                onRequestRamCache = { showRamCacheConfirm = true }
            )
        }
    }

    if (showRamCacheConfirm) {
        AlertDialog(
            onDismissRequest = { showRamCacheConfirm = false },
            title = { Text("¿Activar caché en RAM?") },
            text = {
                Text(
                    "La caché del perfil Máximo se guardará en la memoria RAM del " +
                        "teléfono en vez del almacenamiento interno: lecturas y escrituras " +
                        "mucho más rápidas mientras esté montado. Ocupa esa RAM todo el " +
                        "tiempo que dure el montaje y su contenido se pierde al desmontar " +
                        "o reiniciar (se reconstruye solo, como cualquier caché). Si al " +
                        "montar no hay memoria suficiente, se usa el almacenamiento interno " +
                        "sin más aviso que una línea en Logs."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setRamCache(true)
                    showRamCacheConfirm = false
                }) { Text("Activar") }
            },
            dismissButton = {
                TextButton(onClick = { showRamCacheConfirm = false }) { Text("Cancelar") }
            }
        )
    }

    if (showPerfTest) {
        PerfTestSheet(
            state = vm.perfTest,
            onRun = { vm.startPerfTest() },
            onDismiss = {
                // Cerrar la hoja a mitad de la prueba la corta y borra el archivo temporal.
                vm.cancelPerfTest()
                showPerfTest = false
            }
        )
    }

    if (showPathDialog) {
        FolderPickerDialog(
            initialPath = vm.targetPath,
            onPick = {
                vm.setTargetPath(it)
                showPathDialog = false
            },
            onDismiss = { showPathDialog = false }
        )
    }
}

/**
 * Servidor seleccionado, carpeta de destino y el botón Montar en una sola
 * SectionCard, en ese orden, para que las tres acciones del flujo de
 * montaje queden juntas de un vistazo (v1.5.1). Es la columna izquierda del
 * doble panel en apaisado, o la primera tarjeta de la columna única en
 * vertical.
 */
@Composable
private fun MountCard(
    vm: BindViewModel,
    mounted: Boolean,
    active: String?,
    onOpenServers: () -> Unit,
    onChangeFolder: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val selected = vm.profiles.firstOrNull { it.name == active }
    val label = when {
        vm.busy -> "Trabajando…"
        mounted && (vm.mountedRemote == null || vm.mountedRemote == active) -> "Desmontar"
        mounted -> "Cambiar a $active"
        else -> "Montar"
    }
    SectionCard(title = "Servidor seleccionado", icon = AppIcons.Cloud) {
        Surface(
            color = scheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    // v1.5.2: antes iba en titleLarge/bodyMedium, más grande
                    // que el resto de las tarjetas. Se baja a titleMedium/
                    // bodySmall para que quede al mismo tamaño que los
                    // títulos y descripciones del apartado Rendimiento.
                    Text(selected?.name ?: "Ninguno", style = MaterialTheme.typography.titleMedium)
                    if (selected != null) {
                        Text(
                            if (selected.user.isEmpty()) selected.host else "${selected.user}@${selected.host}",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                    }
                }
                TextButton(onClick = onOpenServers) {
                    Text(if (selected == null) "Agregar" else "Cambiar")
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(AppIcons.Folder, contentDescription = null, tint = scheme.primary)
            Text(
                "Carpeta de destino",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
        }
        Surface(
            color = scheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    vm.targetPath,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onChangeFolder) { Text("Cambiar") }
            }
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
    }
}

/** Interruptor de montaje automático al iniciar el teléfono. */
@Composable
private fun AutostartCard(vm: BindViewModel) {
    SectionCard(
        title = "Montar al iniciar",
        icon = Icons.Default.PlayArrow,
        subtitle = "Monta el servidor seleccionado cuando arranca el teléfono."
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                if (vm.autostart) "Activado" else "Desactivado",
                style = MaterialTheme.typography.titleMedium
            )
            Switch(checked = vm.autostart, onCheckedChange = { vm.setAutostart(it) })
        }
    }
}

/**
 * Perfil de rendimiento (Equilibrado/Máximo), tamaño de caché, prueba de
 * velocidad, caché en RAM (solo en Máximo) y caché en disco.
 */
@Composable
private fun PerfCard(
    vm: BindViewModel,
    mounted: Boolean,
    onRunPerfTest: () -> Unit,
    onRequestRamCache: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    SectionCard(
        title = "Rendimiento",
        icon = AppIcons.Bolt,
        subtitle = "Máximo guarda más en caché para leer y escribir más rápido, a costa de espacio en disco. Se aplica al volver a montar.",
        expandable = true
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PerfMode.entries.forEach { mode ->
                OptionTile(
                    label = mode.label,
                    icon = if (mode == PerfMode.MAX) AppIcons.Bolt else Icons.Default.Settings,
                    selected = vm.perfMode == mode,
                    onClick = { vm.setPerfMode(mode) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // v1.5.2: igual que "Caché en RAM", el tamaño de caché solo tiene
        // sentido en modo Máximo (en Equilibrado no se usa un tamaño
        // configurable), así que se oculta por completo en Equilibrado.
        if (vm.perfMode == PerfMode.MAX) {
            val custom = vm.cacheGb
            val effective = custom ?: defaultCacheGb(vm.perfMode)
            var draft by remember(effective) { mutableStateOf(effective.toFloat()) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Tamaño de caché", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${draft.roundToInt()} GB" + if (custom == null) " (auto)" else "",
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.primary
                )
            }
            Slider(
                value = draft,
                onValueChange = { draft = it },
                onValueChangeFinished = { vm.setCacheGb(draft.roundToInt()) },
                valueRange = CACHE_GB_MIN.toFloat()..CACHE_GB_MAX.toFloat(),
                steps = CACHE_GB_MAX - CACHE_GB_MIN - 1
            )
            Text(
                "Aplica a Google Drive y a FTP en modo Máximo. En Máximo se dejan 2 GB libres para no llenar el almacenamiento.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
            if (custom != null) {
                TextButton(onClick = { vm.setCacheGb(null) }) { Text("Restablecer tamaño automático") }
            }
        }

        FilledTonalButton(
            onClick = onRunPerfTest,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Icon(AppIcons.Bolt, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text("Probar rendimiento", style = MaterialTheme.typography.titleMedium)
        }

        if (vm.perfMode == PerfMode.MAX) {
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Caché en RAM", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Lecturas y escrituras a velocidad de RAM. Se pierde al desmontar o reiniciar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = vm.ramCache,
                    onCheckedChange = { enabled ->
                        if (enabled) onRequestRamCache() else vm.setRamCache(false)
                    }
                )
            }
        }

        HorizontalDivider()

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text("Caché en disco", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (mounted) {
                        "${formatCacheKb(vm.cacheKb)} usados · desmonta para borrarla"
                    } else {
                        "${formatCacheKb(vm.cacheKb)} usados"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
            TextButton(onClick = { vm.clearCache() }, enabled = !mounted && !vm.busy) {
                Text("Borrar caché")
            }
        }
    }
}
