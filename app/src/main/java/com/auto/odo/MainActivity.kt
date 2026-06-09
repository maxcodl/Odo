package com.auto.odo

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.auto.odo.core.NavBarStyle
import com.auto.odo.presentation.theme.OdoTheme
import com.auto.odo.presentation.ui.*
import com.auto.odo.presentation.viewmodel.*
import dagger.hilt.android.AndroidEntryPoint

sealed class Screen(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector?) {
    object Dashboard : Screen("dashboard", "Home", Icons.Default.Home)
    object Logs : Screen("logs", "Logs", Icons.Default.List)
    object Analytics : Screen("analytics", "Stats", Icons.Default.Speed)
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
            val mainViewModel: MainViewModel = hiltViewModel()
            val fullScreenStatusBar by mainViewModel.fullScreenStatusBar.collectAsStateWithLifecycle()
            
            // Modern immersive mode handling
            val view = LocalView.current
            LaunchedEffect(fullScreenStatusBar) {
                val window = (view.context as Activity).window
                val controller = WindowCompat.getInsetsController(window, view)
                if (fullScreenStatusBar) {
                    controller.hide(WindowInsetsCompat.Type.statusBars())
                    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else {
                    controller.show(WindowInsetsCompat.Type.statusBars())
                }
            }

            OdoTheme {
                MainAppScreen(mainViewModel)
            }
        }
    }
}

@Composable
fun MainAppScreen(mainViewModel: MainViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val navBarStyle by mainViewModel.navBarStyle.collectAsStateWithLifecycle()
    val autoHideTitleBar by mainViewModel.autoHideTitleBar.collectAsStateWithLifecycle()
    val fullScreenStatusBar by mainViewModel.fullScreenStatusBar.collectAsStateWithLifecycle()

    val bottomNavigationItems = listOf(
        Screen.Dashboard,
        Screen.Logs,
        Screen.Analytics,
        Screen.Settings
    )

    val isFormRoute = currentRoute == Screen.AddFillUp.route ||
            currentRoute == Screen.AddService.route ||
            currentRoute == Screen.AddExpense.route ||
            currentRoute == Screen.AddTrip.route ||
            currentRoute == Screen.UpdateOdometer.route

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    viewModel = hiltViewModel(),
                    autoHideTitleBar = autoHideTitleBar,
                    fullScreenStatusBar = fullScreenStatusBar,
                    onNavigateToAddFillUp = { navController.navigate(Screen.AddFillUp.route) },
                    onNavigateToAddService = { navController.navigate(Screen.AddService.route) },
                    onNavigateToAddExpense = { navController.navigate(Screen.AddExpense.route) },
                    onNavigateToAddTrip = { navController.navigate(Screen.AddTrip.route) },
                    onNavigateToUpdateOdo = { navController.navigate(Screen.UpdateOdometer.route) },
                    onNavigateToLogs = { navController.navigate(Screen.Logs.route) }
                )
            }

            composable(Screen.Logs.route) {
                LogsFeedScreen(
                    viewModel = hiltViewModel(),
                    autoHideTitleBar = autoHideTitleBar,
                    fullScreenStatusBar = fullScreenStatusBar
                )
            }

            composable(Screen.Analytics.route) {
                AnalyticsMockScreen(fullScreenStatusBar)
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    viewModel = hiltViewModel(),
                    fullScreenStatusBar = fullScreenStatusBar
                )
            }

            composable(Screen.AddFillUp.route) { AddFillUpScreen(hiltViewModel(), { navController.popBackStack() }) }
            composable(Screen.AddService.route) { AddServiceScreen(hiltViewModel(), { navController.popBackStack() }) }
            composable(Screen.AddExpense.route) { AddExpenseScreen(hiltViewModel(), { navController.popBackStack() }) }
            composable(Screen.AddTrip.route) { AddTripScreen(hiltViewModel(), { navController.popBackStack() }) }
            composable(Screen.UpdateOdometer.route) { UpdateOdometerScreen(hiltViewModel(), { navController.popBackStack() }) }
        }

        // Global Floating Navigation Bar overlay
        if (!isFormRoute) {
            FloatingNavigationBar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
                items = bottomNavigationItems,
                currentRoute = currentRoute,
                style = navBarStyle,
                onNavigate = { screen ->
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}

@Composable
fun FloatingNavigationBar(
    modifier: Modifier = Modifier,
    items: List<Screen>,
    currentRoute: String?,
    style: NavBarStyle,
    onNavigate: (Screen) -> Unit
) {
    val backgroundColor = when (style) {
        NavBarStyle.SOLID -> MaterialTheme.colorScheme.surface
        NavBarStyle.BLURRY -> MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
        NavBarStyle.GLASSY -> Color.White.copy(alpha = 0.4f)
    }

    Box(
        modifier = modifier
            .padding(horizontal = 24.dp)
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            shape = RoundedCornerShape(26.dp),
            color = backgroundColor,
            tonalElevation = if (style == NavBarStyle.SOLID) 8.dp else 2.dp,
            border = if (style == NavBarStyle.GLASSY) BorderStroke(0.5.dp, Color.White.copy(alpha = 0.3f)) else null
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { screen ->
                    val isSelected = currentRoute == screen.route
                    val tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    
                    IconButton(
                        onClick = { onNavigate(screen) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(
                                imageVector = screen.icon!!,
                                contentDescription = screen.title,
                                tint = tint,
                                modifier = Modifier.size(26.dp)
                            )
                            AnimatedVisibility(
                                visible = isSelected,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Text(
                                    text = screen.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tint,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsMockScreen(fullScreenStatusBar: Boolean) {
    Scaffold(
        contentWindowInsets = if (fullScreenStatusBar) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
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
}
