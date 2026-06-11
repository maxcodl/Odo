package com.auto.odo

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
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
import androidx.compose.foundation.isSystemInDarkTheme
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
        
        // FIX: Using SystemBarStyle.auto makes both bars transparent and automatically 
        // adjusts the icon colors (clock/battery) for light or dark mode!
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT, 
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = androidx.activity.SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT, 
                android.graphics.Color.TRANSPARENT
            )
        )
        
        setContent {
            val mainViewModel: MainViewModel = hiltViewModel()
            
            val view = LocalView.current
            LaunchedEffect(Unit) {
                val window = (view.context as Activity).window
                val controller = WindowCompat.getInsetsController(window, view)
                controller.show(WindowInsetsCompat.Type.statusBars())
            }

            val appThemeMode by mainViewModel.appThemeMode.collectAsStateWithLifecycle()

            OdoTheme(themeMode = appThemeMode) {
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
    val context = LocalContext.current
    val activity = context as ComponentActivity

    val navBarStyle by mainViewModel.navBarStyle.collectAsStateWithLifecycle()

    val isFormRoute = remember(currentRoute) {
        currentRoute == Screen.AddFillUp.route ||
        currentRoute == Screen.AddService.route ||
        currentRoute == Screen.AddExpense.route ||
        currentRoute == Screen.AddTrip.route ||
        currentRoute == Screen.UpdateOdometer.route
    }

Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        // FIX 1: This MUST be Transparent so the screens can draw all the way down
        containerColor = Color.Transparent, 
        bottomBar = {
            if (!isFormRoute) {
                FloatingNavigationBar(
                    modifier = Modifier.padding(bottom = 24.dp),
                    currentRoute = currentRoute,
                    style = navBarStyle,
                    onNavigate = { screen ->
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
    ) { innerPadding -> 
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier
                .fillMaxSize(), 
                // FIX 2: I completely deleted the `.padding(innerPadding)` here!
                // Now the NavHost can stretch all the way to the absolute bottom of the screen.
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
            composable(Screen.Dashboard.route) {
                val autoHideTitleBar by mainViewModel.autoHideTitleBar.collectAsStateWithLifecycle()
                val fullScreenStatusBar by mainViewModel.fullScreenStatusBar.collectAsStateWithLifecycle()
                DashboardScreen(
                    viewModel = hiltViewModel(activity),
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
                val autoHideTitleBar by mainViewModel.autoHideTitleBar.collectAsStateWithLifecycle()
                val fullScreenStatusBar by mainViewModel.fullScreenStatusBar.collectAsStateWithLifecycle()
                LogsFeedScreen(
                    viewModel = hiltViewModel(activity),
                    autoHideTitleBar = autoHideTitleBar,
                    fullScreenStatusBar = fullScreenStatusBar
                )
            }

composable(Screen.Analytics.route) {
                val fullScreenStatusBar by mainViewModel.fullScreenStatusBar.collectAsStateWithLifecycle()
                AnalyticsScreen(
                    viewModel = hiltViewModel(activity),
                    fullScreenStatusBar = fullScreenStatusBar
                )
            }

            composable(Screen.Settings.route) {
                val autoHideTitleBar by mainViewModel.autoHideTitleBar.collectAsStateWithLifecycle()
                val fullScreenStatusBar by mainViewModel.fullScreenStatusBar.collectAsStateWithLifecycle()
                SettingsScreen(
                    viewModel = hiltViewModel(activity),
                    autoHideTitleBar = autoHideTitleBar,
                    fullScreenStatusBar = fullScreenStatusBar
                )
            }

            composable(
                route = Screen.AddFillUp.route,
                enterTransition = { slideInVertically(initialOffsetY = { it }) + fadeIn() },
                exitTransition = { slideOutVertically(targetOffsetY = { it }) + fadeOut() },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { slideOutVertically(targetOffsetY = { it }) + fadeOut() }
            ) {
                val autoHideTitleBar by mainViewModel.autoHideTitleBar.collectAsStateWithLifecycle()
                val fullScreenStatusBar by mainViewModel.fullScreenStatusBar.collectAsStateWithLifecycle()
                AddFillUpScreen(
                    viewModel = hiltViewModel(),
                    autoHideTitleBar = autoHideTitleBar,
                    fullScreenStatusBar = fullScreenStatusBar,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.AddService.route,
                enterTransition = { slideInVertically(initialOffsetY = { it }) + fadeIn() },
                exitTransition = { slideOutVertically(targetOffsetY = { it }) + fadeOut() },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { slideOutVertically(targetOffsetY = { it }) + fadeOut() }
            ) {
                val autoHideTitleBar by mainViewModel.autoHideTitleBar.collectAsStateWithLifecycle()
                val fullScreenStatusBar by mainViewModel.fullScreenStatusBar.collectAsStateWithLifecycle()
                AddServiceScreen(
                    viewModel = hiltViewModel(),
                    autoHideTitleBar = autoHideTitleBar,
                    fullScreenStatusBar = fullScreenStatusBar,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.AddExpense.route,
                enterTransition = { slideInVertically(initialOffsetY = { it }) + fadeIn() },
                exitTransition = { slideOutVertically(targetOffsetY = { it }) + fadeOut() },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { slideOutVertically(targetOffsetY = { it }) + fadeOut() }
            ) {
                val autoHideTitleBar by mainViewModel.autoHideTitleBar.collectAsStateWithLifecycle()
                val fullScreenStatusBar by mainViewModel.fullScreenStatusBar.collectAsStateWithLifecycle()
                AddExpenseScreen(
                    viewModel = hiltViewModel(),
                    autoHideTitleBar = autoHideTitleBar,
                    fullScreenStatusBar = fullScreenStatusBar,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.AddTrip.route,
                enterTransition = { slideInVertically(initialOffsetY = { it }) + fadeIn() },
                exitTransition = { slideOutVertically(targetOffsetY = { it }) + fadeOut() },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { slideOutVertically(targetOffsetY = { it }) + fadeOut() }
            ) {
                val autoHideTitleBar by mainViewModel.autoHideTitleBar.collectAsStateWithLifecycle()
                val fullScreenStatusBar by mainViewModel.fullScreenStatusBar.collectAsStateWithLifecycle()
                AddTripScreen(
                    viewModel = hiltViewModel(),
                    autoHideTitleBar = autoHideTitleBar,
                    fullScreenStatusBar = fullScreenStatusBar,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.UpdateOdometer.route,
                enterTransition = { slideInVertically(initialOffsetY = { it }) + fadeIn() },
                exitTransition = { slideOutVertically(targetOffsetY = { it }) + fadeOut() },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { slideOutVertically(targetOffsetY = { it }) + fadeOut() }
            ) {
                val autoHideTitleBar by mainViewModel.autoHideTitleBar.collectAsStateWithLifecycle()
                val fullScreenStatusBar by mainViewModel.fullScreenStatusBar.collectAsStateWithLifecycle()
                UpdateOdometerScreen(
                    viewModel = hiltViewModel(),
                    autoHideTitleBar = autoHideTitleBar,
                    fullScreenStatusBar = fullScreenStatusBar,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
fun FloatingNavigationBar(
    modifier: Modifier = Modifier,
    currentRoute: String?,
    style: NavBarStyle,
    onNavigate: (Screen) -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val backgroundColor = when (style) {
        NavBarStyle.SOLID -> MaterialTheme.colorScheme.surface
        NavBarStyle.BLURRY -> MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
        NavBarStyle.GLASSY -> if (isDark) Color.Black.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.45f)
    }
    val borderStroke = when (style) {
        NavBarStyle.GLASSY -> if (isDark) BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)) else BorderStroke(0.5.dp, Color.Black.copy(alpha = 0.12f))
        else -> null
    }

    val items = remember { listOf(Screen.Dashboard, Screen.Logs, Screen.Analytics, Screen.Settings) }

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
            border = borderStroke
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { screen ->
                    key(screen.route) {
                        val isSelected = currentRoute == screen.route

                        val iconScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.15f else 1f,
                            animationSpec = tween(durationMillis = 200),
                            label = "navIconScale_${screen.route}"
                        )
                        val selectedColor = MaterialTheme.colorScheme.primary
                        val unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                        val iconTint by animateColorAsState(
                            targetValue = if (isSelected) selectedColor else unselectedColor,
                            animationSpec = tween(durationMillis = 200),
                            label = "navIconTint_${screen.route}"
                        )

                        IconButton(
                            onClick = { onNavigate(screen) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = screen.icon ?: Icons.Default.Home,
                                    contentDescription = screen.title,
                                    tint = iconTint,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .graphicsLayer(scaleX = iconScale, scaleY = iconScale)
                                )
                                AnimatedVisibility(
                                    visible = isSelected,
                                    enter = fadeIn() + expandVertically(),
                                    exit = fadeOut() + shrinkVertically()
                                ) {
                                    Text(
                                        text = screen.title,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = selectedColor,
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
}
