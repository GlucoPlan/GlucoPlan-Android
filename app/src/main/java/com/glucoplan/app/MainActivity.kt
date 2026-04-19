package com.glucoplan.app
import com.glucoplan.app.ui.chart.GlucoseChartScreen



import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.glucoplan.app.ui.calculator.CalculatorScreen
import com.glucoplan.app.ui.calculator.CalculatorUiState
import com.glucoplan.app.ui.calculator.CalculatorViewModel
import com.glucoplan.app.ui.dishes.DishesScreen
import com.glucoplan.app.ui.dishes.PansScreen
import com.glucoplan.app.ui.history.HistoryScreen
import com.glucoplan.app.ui.products.ProductsScreen
import com.glucoplan.app.ui.settings.SettingsScreen
import com.glucoplan.app.ui.simulator.SimulatorScreen
import com.glucoplan.app.ui.theme.GlucoPlanTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import android.content.Context
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

val Context.dataStore by preferencesDataStore(name = "app_prefs")
val DISCLAIMER_ACCEPTED = booleanPreferencesKey("disclaimer_accepted")

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GlucoPlanTheme {
                GlucoPlanRoot()
            }
        }
    }
}

@Composable
fun GlucoPlanRoot() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val accepted by remember {
        context.dataStore.data.map { it[DISCLAIMER_ACCEPTED] ?: false }
    }.collectAsState(initial = null)

    when (accepted) {
        null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        false -> DisclaimerScreen(onAccepted = {
            scope.launch {
                context.dataStore.edit { it[DISCLAIMER_ACCEPTED] = true }
            }
        })
        true -> MainNavHost()
    }
}

@Composable
fun DisclaimerScreen(onAccepted: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.MedicalInformation, null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(24.dp))
            Text("GlucoPlan",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text("Помощник при диабете 1 типа",
                color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(32.dp))
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    "Приложение является вспомогательным инструментом и не заменяет " +
                    "рекомендации лечащего врача. Все коэффициенты (доза на ХЕ, " +
                    "чувствительность к инсулину, целевой уровень сахара) подбираются " +
                    "совместно с эндокринологом.\n\n" +
                    "Не принимайте решения о дозе инсулина, основываясь только на расчётах приложения.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onAccepted,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Я ознакомлен(а) и согласен(на)", fontSize = 16.sp)
            }
        }
    }
}

sealed class Screen(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Calculator : Screen("calculator", "Расчёт",      Icons.Default.Calculate)
    object Chart      : Screen("chart",      "График",      Icons.Default.Timeline)
    object History    : Screen("history",    "История",     Icons.Default.History)
    object Products   : Screen("products",   "Продукты",    Icons.Default.SetMeal)
    object Dishes     : Screen("dishes",     "Блюда",       Icons.Default.SoupKitchen)
    object Settings   : Screen("settings",   "Настройки",   Icons.Default.Settings)
}

val bottomScreens = listOf(
    Screen.Calculator, Screen.Chart, Screen.History, Screen.Products, Screen.Settings
)

@Composable
fun MainNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Shared CalculatorViewModel so history can load into it
    val calcViewModel: CalculatorViewModel = hiltViewModel()

    var simState by remember { mutableStateOf<CalculatorUiState?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (currentRoute in bottomScreens.map { it.route }) {
                NavigationBar {
                    bottomScreens.forEach { screen ->
                        val selected = currentRoute == screen.route
                        val scale by animateFloatAsState(
                            targetValue = if (selected) 1.18f else 1.0f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                            label = "navIconScale"
                        )
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    screen.icon, screen.label,
                                    modifier = Modifier.scale(scale)
                                )
                            },
                            label = { Text(screen.label, maxLines = 1) },
                            selected = selected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Calculator.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Calculator.route) {
                CalculatorScreen(
                    viewModel = calcViewModel,
                    onNavigateToSimulator = { state ->
                        simState = state
                        navController.navigate("simulator")
                    }
                )
            }
            composable(Screen.Chart.route) {
                GlucoseChartScreen()
            }
            composable(Screen.History.route) {
                HistoryScreen(
                )
            }
            composable(Screen.Products.route) {
                ProductsScreen(
                    onNavigateToDishes = { navController.navigate(Screen.Dishes.route) }
                )
            }
            composable(Screen.Dishes.route) {
                DishesScreen(
                    onNavigateToPans = { navController.navigate("pans") },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(onNavigateToPans = { navController.navigate("pans") })
            }
            composable("simulator") {
                SimulatorScreen(
                    calcState = simState,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("pans") {
                PansScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
