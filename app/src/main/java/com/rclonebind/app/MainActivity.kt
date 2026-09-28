package com.rclonebind.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.rclonebind.app.ui.screens.HomeScreen
import com.rclonebind.app.ui.screens.LogsScreen
import com.rclonebind.app.ui.screens.ServersScreen
import com.rclonebind.app.ui.theme.RCloneTheme
import com.topjohnwu.superuser.Shell
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

/** Alto de la píldora (52 + 2×8 de relleno) + separación por arriba y abajo. */
private val PillSpace = 88.dp

@Composable
private fun AppScaffold(vm: BindViewModel) {
    val items = listOf(Screen.Home, Screen.Servers, Screen.Logs)
    val pagerState = rememberPagerState(pageCount = { items.size })
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

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
            SnackbarHost(snackbarHostState, Modifier.padding(bottom = PillSpace))
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            // Deslizar horizontalmente cambia de pestaña. Las 3 páginas se
            // mantienen compuestas para conservar scroll y estado.
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().padding(bottom = PillSpace),
                beyondViewportPageCount = items.size
            ) { page ->
                when (items[page]) {
                    Screen.Home -> HomeScreen(vm, onOpenServers = { goTo(1) })
                    Screen.Servers -> ServersScreen(vm)
                    Screen.Logs -> LogsScreen(vm)
                }
            }

            FloatingPillNav(
                items = items,
                pagerState = pagerState,
                onSelect = ::goTo,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
            )
        }
    }
}

/**
 * Barra inferior flotante en forma de píldora. Colores del esquema dinámico:
 * fondo primaryContainer, indicador primary, contenido onPrimary /
 * onPrimaryContainer. La pestaña activa muestra icono + etiqueta.
 */
@Composable
private fun FloatingPillNav(
    items: List<Screen>,
    pagerState: PagerState,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, screen ->
                PillItem(
                    screen = screen,
                    selected = pagerState.currentPage == index,
                    onClick = { onSelect(index) }
                )
            }
        }
    }
}

@Composable
private fun PillItem(screen: Screen, selected: Boolean, onClick: () -> Unit) {
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
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(screen.icon, contentDescription = screen.label, tint = content)
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
