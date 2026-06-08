package com.auto.odo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.auto.odo.presentation.theme.OdoTheme
import com.auto.odo.presentation.ui.*
import com.auto.odo.presentation.viewmodel.*
import dagger.hilt.android.AndroidEntryPoint

sealed class Screen(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector?) {
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Home)
    object Logs : Screen("logs", "Logs", Icons.Default.List)
    object Analytics : Screen("analytics", "Analytics", Icons.Default.Speed)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    object AddFillUp : Screen("add_fillup", "Add Fillup", null)
    object AddService : Screen("add_service", "Add Service", null)
    object AddExpense : Screen("add_expense", "Add Expense", null)
    object AddTrip : Screen("add_trip", "Add Trip", null)
    object UpdateOdometer : Screen("update_odo", "Update Odometer", null)
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OdoTheme {
                MainAppScreen()
            }
        }
    }
}

@Composable
fun MainAppScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomNavigationItems = listOf(
        Screen.Dashboard,
        Screen.Logs,
        Screen.Analytics,
        Screen.Settings
    )

    Scaffold(
        bottomBar = {
            // Hide bottom bar on the form screen (AddFillUp) to provide full screen real estate
            val isFormRoute = currentRoute == Screen.AddFillUp.route ||
                    currentRoute == Screen.AddService.route ||
                    currentRoute == Screen.AddExpense.route ||
                    currentRoute == Screen.AddTrip.route ||
                    currentRoute == Screen.UpdateOdometer.route

            if (!isFormRoute) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    bottomNavigationItems.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon!!, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            selected = currentRoute == screen.route,
                            onClick = {
                                if (currentRoute != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Screen.Dashboard.route) {
                val dashboardViewModel: DashboardViewModel = hiltViewModel()
                DashboardScreen(
                    viewModel = dashboardViewModel,
                    onNavigateToAddFillUp = {
                        navController.navigate(Screen.AddFillUp.route)
                    },
                    onNavigateToAddService = {
                        navController.navigate(Screen.AddService.route)
                    },
                    onNavigateToAddExpense = {
                        navController.navigate(Screen.AddExpense.route)
                    },
                    onNavigateToAddTrip = {
                        navController.navigate(Screen.AddTrip.route)
                    },
                    onNavigateToUpdateOdo = {
                        navController.navigate(Screen.UpdateOdometer.route)
                    },
                    onNavigateToLogs = {
                        navController.navigate(Screen.Logs.route)
                    }
                )
            }

            composable(Screen.Logs.route) {
                val logsFeedViewModel: LogsFeedViewModel = hiltViewModel()
                LogsFeedScreen(viewModel = logsFeedViewModel)
            }

            composable(Screen.Analytics.route) {
                // Analytics Mock Screen
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Analytics Hub", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "In-depth analytics, fuel cost projections and efficiency predictions will appear here.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            composable(Screen.Settings.route) {
                val settingsViewModel: SettingsViewModel = hiltViewModel()
                SettingsScreen(viewModel = settingsViewModel)
            }

            composable(Screen.AddFillUp.route) {
                val addFillUpViewModel: AddFillUpViewModel = hiltViewModel()
                AddFillUpScreen(
                    viewModel = addFillUpViewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable(Screen.AddService.route) {
                val addServiceViewModel: AddServiceViewModel = hiltViewModel()
                AddServiceScreen(
                    viewModel = addServiceViewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable(Screen.AddExpense.route) {
                val addExpenseViewModel: AddExpenseViewModel = hiltViewModel()
                AddExpenseScreen(
                    viewModel = addExpenseViewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable(Screen.AddTrip.route) {
                val addTripViewModel: AddTripViewModel = hiltViewModel()
                AddTripScreen(
                    viewModel = addTripViewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable(Screen.UpdateOdometer.route) {
                val updateOdometerViewModel: UpdateOdometerViewModel = hiltViewModel()
                UpdateOdometerScreen(
                    viewModel = updateOdometerViewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
