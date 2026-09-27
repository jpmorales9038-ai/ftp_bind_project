package com.rclonebind.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rclonebind.app.ui.screens.HomeScreen
import com.rclonebind.app.ui.screens.LogsScreen
import com.rclonebind.app.ui.screens.SetupScreen
import com.topjohnwu.superuser.Shell

class MainActivity : ComponentActivity() {

    private val vm: BindViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shell.getShell() // pide root al iniciar
        setContent {
            MaterialTheme {
                AppScaffold(vm)
            }
        }
    }
}

private sealed class Screen(val route: String, val label: String) {
    object Home : Screen("home", "Inicio")
    object Setup : Screen("setup", "Config FTP")
    object Logs : Screen("logs", "Logs")
}

@Composable
private fun AppScaffold(vm: BindViewModel) {
    val navController: NavHostController = rememberNavController()
    val items = listOf(Screen.Home, Screen.Setup, Screen.Logs)

    Scaffold(
        bottomBar = {
            NavigationBar {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination?.route
                items.forEach { screen ->
                    NavigationBarItem(
                        selected = currentRoute == screen.route,
                        onClick = { navController.navigate(screen.route) },
                        icon = {},
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
            composable(Screen.Home.route) { HomeScreen(vm) }
            composable(Screen.Setup.route) { SetupScreen(vm) }
            composable(Screen.Logs.route) { LogsScreen(vm) }
        }
    }
}
