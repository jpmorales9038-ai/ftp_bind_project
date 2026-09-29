package com.rclonebind.app

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.outlined.AccountBox
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.AccountBox
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.rclonebind.app.ui.components.LocalContentBottomInset
import com.rclonebind.app.ui.screens.AboutScreen
import com.rclonebind.app.ui.screens.HomeScreen
import com.rclonebind.app.ui.screens.LogsScreen
import com.rclonebind.app.ui.screens.ServersScreen
import com.rclonebind.app.ui.theme.RCloneTheme
import com.topjohnwu.superuser.Shell
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val vm: BindViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            RCloneTheme {
                AppScaffold(vm)
            }
        }

        // Pedimos root en segundo plano. Si se niega o no hay root
        // disponible, la app sigue abierta y solo lo mostramos en la UI
        // en vez de lanzar una excepción no controlada que la cierra.
        Shell.getShell { shell ->
            vm.setRootGranted(shell.isRoot)
        }
    }
}

/**
 * Cada pestaña trae un ícono en trazo (sin seleccionar) y uno relleno
 * (seleccionada) — el intercambio outlined/filled es el lenguaje que
 * Material Expressive usa en sus barras de navegación en vez de solo
 * cambiar de color. El relleno usa el set Rounded (esquinas suaves) en vez
 * del set Filled por defecto, más anguloso, para que la píldora se vea más
 * armónica con sus propias formas circulares.
 */
private sealed class Screen(val label: String, val filledIcon: ImageVector, val outlinedIcon: ImageVector) {
    object Home : Screen("Inicio", Icons.Rounded.Home, Icons.Outlined.Home)
    object Servers : Screen("Servidores", Icons.Rounded.AccountBox, Icons.Outlined.AccountBox)
    object Logs : Screen("Logs", Icons.AutoMirrored.Rounded.List, Icons.AutoMirrored.Outlined.List)
    object About : Screen("Acerca de", Icons.Rounded.Info, Icons.Outlined.Info)
}

/** Alto de la píldora (52 + 2×8 de relleno) + separación por arriba y abajo. */
private val PillSpace = 88.dp

/** Alto del degradado que funde el contenido con la barra del sistema. */
private val FadeHeight = 104.dp

@Composable
private fun AppScaffold(vm: BindViewModel) {
    val items = listOf(Screen.Home, Screen.Servers, Screen.Logs, Screen.About)
    val pagerState = rememberPagerState(pageCount = { items.size })
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val hazeState = rememberHazeState()

    // No es un ancho de pantalla (eso ya lo maneja cada screen con
    // BoxWithConstraints para su propio layout de dos columnas): es la
    // orientación real del teléfono, la que gira la píldora de la barra
    // inferior a la derecha. android:configChanges en el manifiesto evita
    // que Android recree la Activity al girar, así esta lectura cambia en
    // caliente y hay una composición ya en pantalla sobre la que animar en
    // vez de aparecer ya en su sitio final sin transición.
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    fun goTo(page: Int) {
        scope.launch { pagerState.animateScrollToPage(page) }
    }

    // Atrás desde otra pestaña vuelve a Inicio antes de cerrar la app.
    BackHandler(enabled = pagerState.currentPage != 0) { goTo(0) }

    LaunchedEffect(vm.message) {
        // Se consume DESPUÉS de mostrarlo: cambiar vm.message reinicia este
        // efecto y cancelaría el snackbar antes de que se vea.
        vm.message?.let {
            snackbarHostState.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState, Modifier.padding(bottom = if (isLandscape) 0.dp else PillSpace))
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            // Deslizar horizontalmente cambia de pestaña. Las 4 páginas se
            // mantienen compuestas para conservar scroll y estado. El pager
            // ocupa todo el alto (es la fuente del desenfoque): en modo
            // retrato cada pantalla suma PillSpace a su relleno inferior vía
            // LocalContentBottomInset para no quedar tapada por la píldora
            // flotante; en apaisado la píldora se corre al lateral derecho
            // y ese relleno extra ya no hace falta.
            CompositionLocalProvider(LocalContentBottomInset provides if (isLandscape) 0.dp else PillSpace) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize().hazeSource(hazeState),
                    beyondViewportPageCount = items.size
                ) { page ->
                    when (items[page]) {
                        Screen.Home -> HomeScreen(vm, onOpenServers = { goTo(1) })
                        Screen.Servers -> ServersScreen(vm)
                        Screen.Logs -> LogsScreen(vm)
                        Screen.About -> AboutScreen(vm)
                    }
                }
            }

            // Difuminado inferior: desde la barra de navegación del sistema hacia
            // arriba el contenido se funde con el fondo. Va sobre el pager y bajo
            // la píldora; no intercepta toques. Solo tiene sentido pegado abajo,
            // que es donde vive la píldora en retrato; en apaisado (píldora a la
            // derecha) no hay nada que fundir ahí y se quita.
            if (!isLandscape) {
                val fade = MaterialTheme.colorScheme.background
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(FadeHeight)
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.35f to fade.copy(alpha = 0.25f),
                                0.7f to fade.copy(alpha = 0.7f),
                                1f to fade.copy(alpha = 0.96f)
                            )
                        )
                )
            }

            // Rebote elástico al mudarse de esquina en vez de un salto seco:
            // dos springs (uno por eje) hacia el sesgo de la posición
            // destino. BiasAlignment(0, 1) es abajo centrado (retrato);
            // BiasAlignment(1, 0) es centrado a la derecha (apaisado). Al
            // recomponerse con la nueva orientación cambia el objetivo y
            // ambos springs recorren la distancia con rebote (stiffness
            // baja) hasta asentarse ahí.
            val hBias by animateFloatAsState(
                if (isLandscape) 1f else 0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                label = "pillNavHBias"
            )
            val vBias by animateFloatAsState(
                if (isLandscape) 0f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                label = "pillNavVBias"
            )

            FloatingPillNav(
                items = items,
                pagerState = pagerState,
                hazeState = hazeState,
                onSelect = ::goTo,
                vertical = isLandscape,
                modifier = Modifier
                    .align(BiasAlignment(hBias, vBias))
                    .padding(12.dp)
            )
        }
    }
}

/**
 * Barra flotante en forma de píldora con fondo desenfocado. Colores del
 * esquema dinámico: fondo primaryContainer (translúcido), indicador
 * primary, contenido onPrimary / onPrimaryContainer. En retrato es
 * horizontal y la pestaña activa muestra icono + etiqueta; en apaisado
 * ([vertical]) se apila a la derecha y va solo con iconos, sin etiquetas.
 */
@Composable
private fun FloatingPillNav(
    items: List<Screen>,
    pagerState: PagerState,
    hazeState: HazeState,
    onSelect: (Int) -> Unit,
    vertical: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    // Vidrio esmerilado: desenfoca lo que pasa por debajo y lo tiñe con
    // primaryContainer semitransparente (sigue el color dinámico). Sin
    // Android 12+ Haze cae a un tinte plano.
    Surface(
        modifier = modifier
            .clip(CircleShape)
            .hazeEffect(state = hazeState) {
                backgroundColor = colors.surface
                blurRadius = 24.dp
                noiseFactor = 0f
                tints = listOf(HazeTint(colors.primaryContainer.copy(alpha = 0.55f)))
            },
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, colors.onPrimaryContainer.copy(alpha = 0.12f))
    ) {
        if (vertical) {
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items.forEachIndexed { index, screen ->
                    PillItem(
                        screen = screen,
                        selected = pagerState.currentPage == index,
                        vertical = true,
                        onClick = { onSelect(index) }
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, screen ->
                    PillItem(
                        screen = screen,
                        selected = pagerState.currentPage == index,
                        vertical = false,
                        onClick = { onSelect(index) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PillItem(screen: Screen, selected: Boolean, vertical: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val indicator by animateColorAsState(
        if (selected) colors.primary else Color.Transparent, label = "pillIndicator"
    )
    val content by animateColorAsState(
        if (selected) colors.onPrimary else colors.onPrimaryContainer, label = "pillContent"
    )
    // Rebote elástico al seleccionar, en vez de un simple fundido: es el
    // toque de motion que distingue a Expressive de un cambio de color liso.
    val iconScale by animateFloatAsState(
        if (selected) 1f else 0.86f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pillIconScale"
    )

    val itemModifier = Modifier
        .let { if (vertical) it.size(52.dp) else it.height(52.dp).defaultMinSize(minWidth = 52.dp) }
        .clip(CircleShape)
        .background(indicator)
        .selectable(
            selected = selected,
            role = Role.Tab,
            onClick = {
                // Solo vibra si de verdad cambia de pestaña; volver a
                // tocar la ya activa no dispara nada porque no pasa nada.
                // SegmentTick es el patrón corto que usa Android para
                // saltar entre segmentos/pestañas (distinto del de
                // encender/apagar un switch o mantener presionado).
                if (!selected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onClick()
            }
        )

    // En apaisado va solo el icono, sin etiqueta ni aunque esté
    // seleccionada: no hay ancho sobrante en una barra vertical angosta
    // para desplegar texto al lado sin desbordar la pantalla.
    if (vertical) {
        Box(itemModifier, contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (selected) screen.filledIcon else screen.outlinedIcon,
                contentDescription = screen.label,
                tint = content,
                modifier = Modifier.size(26.dp).scale(iconScale)
            )
        }
        return
    }

    Row(
        modifier = itemModifier.padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (selected) screen.filledIcon else screen.outlinedIcon,
            contentDescription = screen.label,
            tint = content,
            // Sin tamaño explícito quedan en 24dp (el default de Icon). 28dp
            // es "un poco más grande" sin desbalancear la altura de 52dp de
            // la píldora ni el texto labelLarge de al lado.
            modifier = Modifier.size(28.dp).scale(iconScale)
        )
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally()
        ) {
            Text(
                text = screen.label,
                color = content,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp, end = 4.dp)
            )
        }
    }
}
