package com.rclonebind.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rclonebind.app.ui.screens.HomeScreen
import com.rclonebind.app.ui.screens.LogsScreen
import com.rclonebind.app.ui.screens.ServersScreen
import com.rclonebind.app.ui.theme.RCloneTheme
import com.topjohnwu.superuser.Shell

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

private sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home : Screen("home", "Inicio", Icons.Default.Home)
    object Servers : Screen("servers", "Servidores", Icons.Default.AccountBox)
    object Logs : Screen("logs", "Logs", Icons.AutoMirrored.Filled.List)
}

private fun NavHostController.goTo(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun AppScaffold(vm: BindViewModel) {
    val navController: NavHostController = rememberNavController()
    val items = listOf(Screen.Home, Screen.Servers, Screen.Logs)
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(vm.message) {
        // Se consume DESPUÉS de mostrarlo: cambiar vm.message reinicia este
        // efecto y cancelaría el snackbar antes de que se vea.
        vm.message?.let {
            snackbarHostState.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination?.route
                items.forEach { screen ->
                    NavigationBarItem(
                        selected = currentRoute == screen.route,
                        onClick = { navController.goTo(screen.route) },
                        icon = { Icon(screen.icon, contentDescription = null) },
                        label = { Text(screen.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(vm, onOpenServers = { navController.goTo(Screen.Servers.route) })
            }
            composable(Screen.Servers.route) { ServersScreen(vm) }
            composable(Screen.Logs.route) { LogsScreen(vm) }
        }
    }
}
