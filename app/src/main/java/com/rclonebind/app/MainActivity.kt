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
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Home
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.rclonebind.app.ui.components.LocalContentBottomInset
import com.rclonebind.app.ui.components.LocalContentEndInset
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

private sealed class Screen(val label: String, val icon: ImageVector) {
    object Home : Screen("Inicio", Icons.Default.Home)
    object Servers : Screen("Servidores", Icons.Default.AccountBox)
    object Logs : Screen("Logs", Icons.AutoMirrored.Filled.List)
}

/** Grosor de la píldora (52 de icono + 2×8 de relleno) más margen hasta el borde. */
private val PillThickness = 68.dp
private val PillMargin = 20.dp

/** Espacio que la píldora le reserva al contenido, en la orientación que sea. */
private val PillClearance = PillThickness + PillMargin

/** Alto/ancho del degradado que funde el contenido con la barra del sistema. */
private val FadeSize = 104.dp

@Composable
private fun AppScaffold(vm: BindViewModel) {
    val items = listOf(Screen.Home, Screen.Servers, Screen.Logs)
    val pagerState = rememberPagerState(pageCount = { items.size })
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val hazeState = rememberHazeState()

    // La Activity declara configChanges para orientación en el manifest, así
    // que al rotar NO se recrea: solo cambia LocalConfiguration y Compose
    // recompone, lo que permite animar el traslado de la píldora en vez de
    // que la pantalla se reconstruya de golpe.
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
            SnackbarHost(
                snackbarHostState,
                Modifier.padding(bottom = if (isLandscape) 12.dp else PillClearance)
            )
        }
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val containerWidthPx = constraints.maxWidth
            val containerHeightPx = constraints.maxHeight

            // Deslizar horizontalmente cambia de pestaña. Las 3 páginas se
            // mantienen compuestas para conservar scroll y estado. El pager
            // ocupa todo el espacio (es la fuente del desenfoque); cada
            // pantalla suma la reserva de la píldora al lado que corresponda
            // vía LocalContentBottomInset/LocalContentEndInset.
            CompositionLocalProvider(
                LocalContentBottomInset provides if (isLandscape) 0.dp else PillClearance,
                LocalContentEndInset provides if (isLandscape) PillClearance else 0.dp
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize().hazeSource(hazeState),
                    beyondViewportPageCount = items.size
                ) { page ->
                    when (items[page]) {
                        Screen.Home -> HomeScreen(vm, onOpenServers = { goTo(1) })
                        Screen.Servers -> ServersScreen(vm)
                        Screen.Logs -> LogsScreen(vm)
                    }
                }
            }

            // Difuminado: funde el contenido con la barra del sistema. Abajo
            // en vertical, a la derecha en apaisado (mismo lado que la
            // píldora). Va sobre el pager y bajo la píldora; no intercepta toques.
            val fade = MaterialTheme.colorScheme.background
            val fadeBrush = if (isLandscape) {
                Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.3f to fade.copy(alpha = 0.25f),
                    0.65f to fade.copy(alpha = 0.7f),
                    1f to fade.copy(alpha = 0.96f)
                )
            } else {
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.35f to fade.copy(alpha = 0.25f),
                    0.7f to fade.copy(alpha = 0.7f),
                    1f to fade.copy(alpha = 0.96f)
                )
            }
            Box(
                Modifier
                    .align(if (isLandscape) Alignment.CenterEnd else Alignment.BottomCenter)
                    .then(if (isLandscape) Modifier.fillMaxHeight().width(FadeSize) else Modifier.fillMaxWidth().height(FadeSize))
                    .background(fadeBrush)
            )

            // Tamaño real de la píldora ya renderizada, para poder centrarla
            // contra el borde que corresponda (cambia de alto en vertical a
            // ancho en horizontal al pasar de fila a columna).
            var pillSizePx by remember { mutableStateOf(IntSize.Zero) }
            val marginPx = with(LocalDensity.current) { 12.dp.roundToPx() }

            val targetOffset = if (isLandscape) {
                IntOffset(
                    x = containerWidthPx - pillSizePx.width - marginPx,
                    y = (containerHeightPx - pillSizePx.height) / 2
                )
            } else {
                IntOffset(
                    x = (containerWidthPx - pillSizePx.width) / 2,
                    y = containerHeightPx - pillSizePx.height - marginPx
                )
            }
            // Resorte con algo de rebote: la píldora se desliza y se reacomoda
            // sola al girar el teléfono, en vez de saltar de golpe a su lugar.
            val animatedOffset by animateIntOffsetAsState(
                targetOffset,
                animationSpec = spring(dampingRatio = 0.75f, stiffness = 260f),
                label = "pillOffset"
            )

            FloatingPillNav(
                items = items,
                pagerState = pagerState,
                hazeState = hazeState,
                isVertical = isLandscape,
                onSelect = ::goTo,
                modifier = Modifier
                    .onSizeChanged { pillSizePx = it }
                    .offset { animatedOffset }
            )
        }
    }
}

/**
 * Barra de navegación flotante en forma de píldora, con fondo desenfocado.
 * En vertical va abajo al centro con iconos + etiqueta en la pestaña activa;
 * en apaisado pasa a ser una columna pegada al borde derecho, solo con
 * iconos (sin etiquetas, para no ocupar tanto ancho de la pantalla).
 */
@Composable
private fun FloatingPillNav(
    items: List<Screen>,
    pagerState: PagerState,
    hazeState: HazeState,
    isVertical: Boolean,
    onSelect: (Int) -> Unit,
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
            }
            // Anima el cambio de tamaño al pasar de fila (con etiqueta) a
            // columna (solo iconos), o al perder/ganar la etiqueta.
            .animateContentSize(spring(dampingRatio = 0.85f, stiffness = 300f)),
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, colors.onPrimaryContainer.copy(alpha = 0.12f))
    ) {
        if (isVertical) {
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items.forEachIndexed { index, screen ->
                    PillItem(
                        screen = screen,
                        selected = pagerState.currentPage == index,
                        showLabel = false,
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
                        showLabel = true,
                        onClick = { onSelect(index) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PillItem(screen: Screen, selected: Boolean, showLabel: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val indicator by animateColorAsState(
        if (selected) colors.primary else Color.Transparent, label = "pillIndicator"
    )
    val content by animateColorAsState(
        if (selected) colors.onPrimary else colors.onPrimaryContainer, label = "pillContent"
    )

    Row(
        modifier = Modifier
            .height(52.dp)
            .defaultMinSize(minWidth = 52.dp)
            .clip(CircleShape)
            .background(indicator)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = if (showLabel) 14.dp else 0.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(screen.icon, contentDescription = screen.label, tint = content)
        // Siempre se declara (con AnimatedVisibility) para que aparecer y
        // desaparecer anime; en apaisado showLabel es false y nunca se ve.
        AnimatedVisibility(
            visible = selected && showLabel,
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
