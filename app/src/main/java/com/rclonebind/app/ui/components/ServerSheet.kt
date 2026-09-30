package com.rclonebind.app.ui.components

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rclonebind.app.BuildConfig
import com.rclonebind.app.net.FoundFtpServer
import com.rclonebind.app.net.scanForFtpServers
import com.rclonebind.app.root.DriveAuthState
import com.rclonebind.app.root.DriveOptions
import com.rclonebind.app.root.RemoteProfile
import com.rclonebind.app.root.RemoteType
import com.rclonebind.app.root.cleanHost
import com.rclonebind.app.root.extractDriveFolderId
import com.rclonebind.app.root.normalizeToken
import com.rclonebind.app.root.validateProfileName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Formulario para agregar un servidor (FTP o Google Drive), o editar
 * [initial] si no es null. El tipo no se puede cambiar al editar.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ServerSheet(
    initial: RemoteProfile?,
    // Solo aplica cuando initial es null (servidor nuevo): qué chip queda
    // marcado al abrir. Por ejemplo, el panel de Google Drive en el doble
    // panel de Servidores abre el formulario ya en Drive en vez de FTP.
    initialType: RemoteType = RemoteType.FTP,
    existingNames: List<String>,
    driveAuth: DriveAuthState,
    onDriveLogin: (clientId: String, clientSecret: String) -> Unit,
    onDriveCancel: () -> Unit,
    onSaveFtp: (name: String, host: String, port: String, user: String, pass: String) -> Unit,
    onSaveDrive: (name: String, token: String?, options: DriveOptions) -> Unit,
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

    var type by remember { mutableStateOf(initial?.type ?: initialType) }

    // Estado de Google Drive
    val initialDrive = initial?.drive
    // Los campos avanzados son solo para un cliente OAuth propio; vacíos = el que trae la app.
    var clientId by remember {
        mutableStateOf(initialDrive?.clientId?.takeUnless { it == BuildConfig.GDRIVE_CLIENT_ID }.orEmpty())
    }
    var clientSecret by remember {
        mutableStateOf(initialDrive?.clientSecret?.takeUnless { it == BuildConfig.GDRIVE_CLIENT_SECRET }.orEmpty())
    }
    var readOnly by remember { mutableStateOf(initialDrive?.readOnly ?: false) }
    var rootFolder by remember { mutableStateOf(initialDrive?.rootFolderId ?: "") }
    var teamDrive by remember { mutableStateOf(initialDrive?.teamDrive ?: "") }
    var acknowledgeAbuse by remember { mutableStateOf(initialDrive?.acknowledgeAbuse ?: false) }
    var showAdvanced by remember { mutableStateOf(false) }
    var showManual by remember { mutableStateOf(false) }
    var manualToken by remember { mutableStateOf("") }
    // Token nuevo (login o pegado). Null al editar = se conserva la sesión guardada.
    var newToken by remember { mutableStateOf<String?>(null) }
    var driveError by remember { mutableStateOf<String?>(null) }

    // Cliente OAuth efectivo: el propio si se escribió; si no, el de la app. Un
    // remoto viejo creado con el cliente compartido de rclone (sin client_id
    // guardado) conserva ese cliente para no invalidar su sesión.
    val useBuiltIn = !(initialDrive != null && initialDrive.clientId.isEmpty())
    val effectiveClientId = clientId.trim().ifEmpty { if (useBuiltIn) BuildConfig.GDRIVE_CLIENT_ID else "" }
    val effectiveClientSecret = clientSecret.trim().ifEmpty { if (useBuiltIn) BuildConfig.GDRIVE_CLIENT_SECRET else "" }

    // Cambiar el cliente OAuth invalida la sesión guardada: hay que volver a entrar.
    val clientChanged = initialDrive != null &&
        (effectiveClientId != initialDrive.clientId || effectiveClientSecret != initialDrive.clientSecret)
    val hasSession = newToken != null || (initialDrive?.hasToken == true && !clientChanged)
    val loginRunning = driveAuth is DriveAuthState.Starting || driveAuth is DriveAuthState.WaitingBrowser

    val appContext = LocalContext.current
    LaunchedEffect(driveAuth) {
        when (driveAuth) {
            is DriveAuthState.Success -> {
                newToken = driveAuth.token
                driveError = null
            }
            // Se abre solo una vez por inicio de sesión: la clave es el propio estado.
            is DriveAuthState.WaitingBrowser -> {
                if (!openInBrowser(appContext, driveAuth.url)) {
                    driveError = "No se encontró un navegador. Copia el enlace y ábrelo a mano."
                }
            }
            else -> Unit
        }
    }

    // Detector de servidores FTP en la red local (solo llena Host/Puerto;
    // el nombre lo sigue eligiendo el usuario).
    val scope = rememberCoroutineScope()
    var scanJob by remember { mutableStateOf<Job?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var scanChecked by remember { mutableStateOf(0) }
    var scanTotal by remember { mutableStateOf(1) }
    val context = LocalContext.current
    var scanResults by remember { mutableStateOf<List<FoundFtpServer>>(emptyList()) }
    var scanMessage by remember { mutableStateOf<String?>(null) }

    fun startScan() {
        scanResults = emptyList()
        scanMessage = null
        scanChecked = 0
        scanTotal = 1
        scanning = true
        scanJob = scope.launch {
            try {
                val found = scanForFtpServers(context) { checked, total -> scanChecked = checked; scanTotal = total }
                scanResults = found
                scanMessage = if (found.isEmpty()) "No se encontró ningún servidor FTP. Verifica que estés en Wi-Fi y que el servidor esté encendido (puertos 21, 2121, 2221, 2222)." else null
            } catch (e: CancellationException) {
                throw e // el usuario tocó "Cancelar búsqueda"; no es un error
            } catch (e: Exception) {
                // Antes, cualquier excepción acá dejaba el botón trabado en
                // "Cancelar búsqueda…" para siempre sin ningún aviso.
                scanMessage = "No se pudo completar la búsqueda: ${e.message ?: e::class.simpleName}"
            } finally {
                scanning = false
            }
        }
    }

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

            if (initial == null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RemoteType.entries.forEach { option ->
                        OptionTile(
                            label = option.label,
                            icon = if (option == RemoteType.FTP) AppIcons.Dns else AppIcons.DriveLogo,
                            selected = type == option,
                            onClick = { type = option },
                            brandIcon = option == RemoteType.DRIVE,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

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

            if (type == RemoteType.FTP) {
                OutlinedButton(
                    onClick = {
                        if (scanning) {
                            scanJob?.cancel()
                            scanning = false
                        } else {
                            startScan()
                        }
                    },
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (scanning) "Cancelar búsqueda ($scanChecked/$scanTotal)" else "Buscar servidores FTP en mi red")
                }

                if (scanning) {
                    LinearWavyProgressIndicator(
                        progress = { scanChecked / scanTotal.toFloat() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                scanMessage?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (scanResults.isNotEmpty()) {
                    Text("Toca uno para usarlo", style = MaterialTheme.typography.labelLarge)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        scanResults.forEach { server ->
                            Surface(
                                onClick = {
                                    host = server.ip
                                    hostError = null
                                    port = server.port.toString()
                                    portError = null
                                    scanResults = emptyList()
                                },
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("${server.ip}:${server.port}", style = MaterialTheme.typography.bodyLarge)
                                    if (!server.banner.isNullOrBlank()) {
                                        Text(
                                            server.banner,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

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
            } else {
                // ---- Google Drive ----
                Text(
                    when {
                        newToken != null -> "Cuenta de Google conectada. Guarda para aplicarla."
                        hasSession -> "Ya hay una sesión de Google guardada."
                        clientChanged -> "Cambiaste el cliente OAuth: vuelve a iniciar sesión."
                        else -> "Inicia sesión con tu cuenta de Google para dar acceso a Drive."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = {
                        if (effectiveClientId.isEmpty() != effectiveClientSecret.isEmpty()) {
                            driveError = "Client ID y Client Secret van juntos: llena los dos o ninguno."
                            showAdvanced = true
                        } else {
                            driveError = null
                            onDriveLogin(effectiveClientId, effectiveClientSecret)
                        }
                    },
                    enabled = !loginRunning,
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4285F4), // Azul Google
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFF4285F4).copy(alpha = 0.38f),
                        disabledContentColor = Color.White.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text(if (hasSession) "Volver a iniciar sesión" else "Iniciar sesión con Google")
                }

                when (driveAuth) {
                    is DriveAuthState.Starting -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("Iniciando…", style = MaterialTheme.typography.bodyMedium)
                    }
                    is DriveAuthState.WaitingBrowser -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            "Autoriza el acceso en el navegador y vuelve a esta app. " +
                                "Verás «Success!» cuando termine.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                if (!openInBrowser(appContext, driveAuth.url)) {
                                    driveError = "No se encontró un navegador. Copia el enlace y ábrelo a mano."
                                }
                            }) { Text("Abrir de nuevo") }
                            TextButton(onClick = { copyToClipboard(appContext, driveAuth.url) }) { Text("Copiar enlace") }
                            TextButton(onClick = onDriveCancel) { Text("Cancelar") }
                        }
                    }
                    is DriveAuthState.Failed -> Text(
                        driveAuth.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    else -> Unit
                }

                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Solo lectura", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Impide modificar o borrar archivos de Drive desde la carpeta montada.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = readOnly, onCheckedChange = { readOnly = it })
                }

                TextButton(onClick = { showAdvanced = !showAdvanced }) {
                    Text(if (showAdvanced) "Ocultar opciones avanzadas" else "Opciones avanzadas")
                }
                if (showAdvanced) {
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.weight(1f).padding(end = 12.dp)) {
                            Text("Permitir archivos marcados como malware", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Descarga archivos que Google Drive bloquea como malware o spam " +
                                    "(error 403 cannotDownloadAbusiveFile). Actívalo solo si confías en el contenido.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = acknowledgeAbuse, onCheckedChange = { acknowledgeAbuse = it })
                    }
                    OutlinedTextField(
                        value = rootFolder,
                        onValueChange = { rootFolder = extractDriveFolderId(it) },
                        label = { Text("ID de carpeta raíz (opcional)") },
                        supportingText = { Text("Monta solo esa carpeta en vez de todo Mi unidad. Puedes pegar el link para compartir: se toma solo el ID.") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = teamDrive,
                        onValueChange = { teamDrive = it },
                        label = { Text("ID de unidad compartida (opcional)") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = clientId,
                        onValueChange = { clientId = it; driveError = null },
                        label = { Text("Client ID propio (opcional)") },
                        supportingText = {
                            Text(
                                if (BuildConfig.GDRIVE_CLIENT_ID.isNotEmpty()) "Vacío = el que trae la app."
                                else "Sin esto se usa el compartido de rclone, con cuota limitada."
                            )
                        },
                        singleLine = true,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = clientSecret,
                        onValueChange = { clientSecret = it; driveError = null },
                        label = { Text("Client Secret propio (opcional)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(onClick = { showManual = !showManual }) {
                        Text(if (showManual) "Ocultar token manual" else "Pegar token manualmente")
                    }
                    if (showManual) {
                        Text(
                            "En un PC ejecuta: rclone authorize \"drive\" y pega aquí el bloque JSON que imprime.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = manualToken,
                            onValueChange = { manualToken = it },
                            label = { Text("Token (JSON)") },
                            minLines = 3,
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedButton(
                            onClick = {
                                val normalized = normalizeToken(manualToken)
                                if (normalized == null) {
                                    driveError = "Token inválido: debe traer access_token y refresh_token."
                                } else {
                                    newToken = normalized
                                    driveError = null
                                    manualToken = ""
                                    showManual = false
                                }
                            },
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Usar este token") }
                    }
                }

                driveError?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }

            Button(
                onClick = {
                    val cleanName = name.trim()
                    nameError = validateProfileName(cleanName, initial?.name, existingNames)
                    if (type == RemoteType.FTP) {
                        hostError = if (cleanHost(host).isEmpty()) "Escribe la dirección del servidor" else null
                        val portNumber = port.trim().toIntOrNull()
                        portError = if (portNumber == null || portNumber !in 1..65535) "Usa un puerto entre 1 y 65535" else null
                        if (nameError == null && hostError == null && portError == null) {
                            onSaveFtp(cleanName, host, port, user, pass)
                        }
                    } else {
                        driveError = when {
                            effectiveClientId.isEmpty() != effectiveClientSecret.isEmpty() ->
                                "Client ID y Client Secret van juntos: llena los dos o ninguno."
                            !hasSession -> "Inicia sesión con Google antes de guardar."
                            else -> null
                        }
                        if (nameError == null && driveError == null) {
                            onSaveDrive(
                                cleanName,
                                newToken,
                                DriveOptions(
                                    // Siempre se guarda el cliente usado en el login: el
                                    // refresh token solo vale con ese mismo cliente.
                                    clientId = effectiveClientId,
                                    clientSecret = effectiveClientSecret,
                                    readOnly = readOnly,
                                    rootFolderId = rootFolder.trim(),
                                    teamDrive = teamDrive.trim(),
                                    acknowledgeAbuse = acknowledgeAbuse
                                )
                            )
                        }
                    }
                },
                enabled = !loginRunning,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text(if (initial == null) "Guardar servidor" else "Guardar cambios")
            }
        }
    }
}

/** Abre [url] en el navegador del sistema. Devuelve false si no hay ninguno. */
private fun openInBrowser(context: Context, url: String): Boolean =
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (e: ActivityNotFoundException) {
        false
    }

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Enlace de Google", text))
}
