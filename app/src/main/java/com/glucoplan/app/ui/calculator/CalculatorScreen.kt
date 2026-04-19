package com.glucoplan.app.ui.calculator

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glucoplan.app.ui.theme.GlucoseColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(
    viewModel: CalculatorViewModel = hiltViewModel(),
    onNavigateToSimulator: (CalculatorUiState) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddMenu by remember { mutableStateOf(false) }
    var showAddProduct by remember { mutableStateOf(false) }
    var showAddDish by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showGlucoseDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.savedSuccess) {
        if (state.savedSuccess) snackbarHostState.showSnackbar("Приём записан")
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar("Ошибка: $it"); viewModel.clearError() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("GlucoPlan") },
                actions = {
                    if (state.components.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Default.DeleteSweep, "Очистить")
                        }
                    }
                    IconButton(onClick = { onNavigateToSimulator(state) }) {
                        Icon(Icons.Default.ShowChart, "Симулятор")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddMenu = true }) {
                Icon(Icons.Default.Add, "Добавить")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // CGM widget
            if (state.settings.nsEnabled) {
                CgmWidget(reading = state.cgmReading, error = state.cgmError)
            }

            // Component list
            if (state.components.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(120.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.RestaurantMenu, null,
                                    modifier = Modifier.size(56.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                        Text(
                            "Добавьте продукты или блюда",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Нажмите кнопку + внизу экрана",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 8.dp)) {
                    items(state.components, key = { it.key }) { comp ->
                        ComponentCard(
                            component = comp,
                            onWeightChanged = { viewModel.updateWeight(comp.key, it) },
                            onRemove = { viewModel.removeComponent(comp.key) },
                            onToggleAdjust = { viewModel.toggleAdjustment(comp.key) }
                        )
                    }
                }
            }

            // Totals panel
            TotalsPanel(state = state)

            // Insulin panel
            InsulinPanel(
                state = state,
                onEditGlucose = { showGlucoseDialog = true },
                onSave = { showSaveDialog = true },
                onAdjust = { viewModel.adjustPortion(it) }
            )
        }
    }

    // ─── Диалог добавления (BottomSheet) ───────────────────────────────────────
    if (showAddMenu) {
        ModalBottomSheet(onDismissRequest = { showAddMenu = false }) {
            Text(
                "Добавить в расчёт",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
            )
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            ListItem(
                headlineContent = { Text("Продукт") },
                supportingContent = { Text("Из базы продуктов", style = MaterialTheme.typography.bodySmall) },
                leadingContent = {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.SetMeal, null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
                modifier = Modifier.clickable { showAddProduct = true; showAddMenu = false }
            )
            ListItem(
                headlineContent = { Text("Блюдо") },
                supportingContent = { Text("Составное блюдо по рецепту", style = MaterialTheme.typography.bodySmall) },
                leadingContent = {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Restaurant, null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
                modifier = Modifier.clickable { showAddDish = true; showAddMenu = false }
            )
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showAddProduct) {
        AddComponentDialog(
            type = ComponentDialogType.PRODUCT,
            onDismiss = { showAddProduct = false },
            onAddProduct = { product, weight ->
                viewModel.addProduct(product, weight)
                showAddProduct = false
            },
            onAddDish = { _, _ -> }
        )
    }

    if (showAddDish) {
        AddComponentDialog(
            type = ComponentDialogType.DISH,
            onDismiss = { showAddDish = false },
            onAddProduct = { _, _ -> },
            onAddDish = { dish, weight ->
                viewModel.addDish(dish, weight)
                showAddDish = false
            }
        )
    }

    if (showSaveDialog) {
        SaveMealDialog(
            state = state,
            onDismiss = { showSaveDialog = false },
            onSave = { notes, sendNs ->
                viewModel.saveMeal(notes, sendNs)
                showSaveDialog = false
            }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Очистить список?") },
            text = { Text("Все добавленные компоненты будут удалены.") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearAll(); showClearDialog = false }) {
                    Text("Очистить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Отмена") }
            }
        )
    }

    if (showGlucoseDialog) {
        GlucoseInputDialog(
            current = state.currentGlucose,
            onDismiss = { showGlucoseDialog = false },
            onConfirm = { viewModel.updateGlucose(it); showGlucoseDialog = false }
        )
    }
}

// ─── CgmWidget ────────────────────────────────────────────────────────────────

@Composable
fun CgmWidget(
    reading: com.glucoplan.app.domain.model.CgmReading?,
    error: String? = null
) {
    val color = if (reading == null || reading.isStale) MaterialTheme.colorScheme.outline
    else GlucoseColor(reading.glucose, 3.9, 10.0)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (reading == null) {
                if (error != null) {
                    Icon(Icons.Default.WifiOff, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Нет соединения с CGM", color = MaterialTheme.colorScheme.error)
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Загрузка CGM…", color = MaterialTheme.colorScheme.outline)
                }
            } else {
                Text(
                    "%.1f".format(reading.glucose),
                    fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, color = color
                )
                Spacer(Modifier.width(4.dp))
                Text(reading.directionArrow, fontSize = 22.sp, color = color)
                Spacer(Modifier.width(8.dp))
                if (reading.isStale) {
                    Text("(устаревшее)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    reading.forecast20min?.let {
                        Text(
                            "→ %.1f через 20 мин".format(it),
                            fontSize = 12.sp,
                            color = color.copy(alpha = 0.7f)
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                val diffMin = java.time.Instant.now().epochSecond / 60 - reading.time.epochSecond / 60
                Text(
                    if (diffMin < 1) "только что" else "$diffMin мин назад",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ─── TotalsPanel ──────────────────────────────────────────────────────────────

@Composable
fun TotalsPanel(state: CalculatorUiState) {
    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            // Главное: УВ, ХЕ, ГН
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TotalStat("УВ", "%.1f г".format(state.totalCarbs), MaterialTheme.colorScheme.primary, labelSp = 11, valueSp = 15)
                TotalStat("ХЕ", "%.1f".format(state.breadUnits), MaterialTheme.colorScheme.secondary, labelSp = 11, valueSp = 15)
                TotalStat("ГН", "%.1f".format(state.glycemicLoad), MaterialTheme.colorScheme.tertiary, labelSp = 11, valueSp = 15)
            }
            Spacer(Modifier.height(6.dp))
            // Дополнительно: Б, Ж, Ккал
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TotalStat("Белки", "%.1f г".format(state.totalProteins), null, labelSp = 10, valueSp = 12)
                TotalStat("Жиры", "%.1f г".format(state.totalFats), null, labelSp = 10, valueSp = 12)
                TotalStat("Ккал", "%.0f".format(state.totalCalories), null, labelSp = 10, valueSp = 12)
            }
        }
    }
}

@Composable
private fun TotalStat(label: String, value: String, color: Color?, labelSp: Int = 10, valueSp: Int = 13) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = labelSp.sp, color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(2.dp))
        Text(value, fontWeight = FontWeight.Bold, fontSize = valueSp.sp,
            color = color ?: MaterialTheme.colorScheme.onSurface)
    }
}

// ─── InsulinPanel ─────────────────────────────────────────────────────────────

@Composable
fun InsulinPanel(
    state: CalculatorUiState,
    onEditGlucose: () -> Unit,
    onSave: () -> Unit,
    onAdjust: (Double) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var roundUp by remember { mutableStateOf(true) }
    val roundedDose = if (roundUp) state.roundedUp else state.roundedDown
    val glucoseColor = if (state.currentGlucose > 0)
        GlucoseColor(state.currentGlucose, state.settings.targetGlucoseMin, state.settings.targetGlucoseMax)
    else null

    Surface(
        tonalElevation = 4.dp,
        shape = MaterialTheme.shapes.large.copy(
            bottomStart = androidx.compose.foundation.shape.CornerSize(0.dp),
            bottomEnd = androidx.compose.foundation.shape.CornerSize(0.dp)
        ),
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Drag handle
            Box(modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(48.dp).height(5.dp)
                .background(MaterialTheme.colorScheme.outlineVariant, CircleShape)
            )
            Spacer(Modifier.height(12.dp))

            // Header row
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Кнопка ввода глюкозы
                Surface(
                    onClick = onEditGlucose,
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Text("Сахар", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        Text(
                            if (state.currentGlucose > 0) "%.1f ммоль/л".format(state.currentGlucose)
                            else "Введите сахар",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = glucoseColor ?: MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                // Итоговая доза
                Column(horizontalAlignment = Alignment.End) {
                    Text("ИТОГО", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "%.1f".format(roundedDose),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            " ед",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = onSave,
                    enabled = state.components.isNotEmpty()
                ) {
                    Text("Записать")
                }
            }

            // Expanded content
            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))

                    InsulinRow("На еду:", "%.2f ед".format(state.foodDose))
                    if (state.correction > 0)
                        InsulinRow("Коррекция сахара:", "%.2f ед".format(state.correction), MaterialTheme.colorScheme.tertiary)
                    if (state.trendDelta != 0.0)
                        InsulinRow(
                            "Поправка тренда CGM:",
                            "${if (state.trendDelta > 0) "+" else ""}%.1f ед".format(state.trendDelta),
                            MaterialTheme.colorScheme.primary
                        )
                    InsulinRow("Точная доза:", "%.2f ед".format(state.totalDose))

                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Округлить: ", fontSize = 13.sp)
                        Spacer(Modifier.width(8.dp))
                        FilterChip(
                            selected = !roundUp,
                            onClick = { roundUp = false },
                            label = { Text("↓ %.1f".format(state.roundedDown)) }
                        )
                        Spacer(Modifier.width(8.dp))
                        FilterChip(
                            selected = roundUp,
                            onClick = { roundUp = true },
                            label = { Text("↑ %.1f".format(state.roundedUp)) }
                        )
                    }

                    if (state.components.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { onAdjust(roundedDose) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Tune, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Скорректировать порцию")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InsulinRow(label: String, value: String, color: Color? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            color = color ?: MaterialTheme.colorScheme.onSurface)
    }
}

// ─── Диалоги ──────────────────────────────────────────────────────────────────

@Composable
fun GlucoseInputDialog(current: Double, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var text by remember { mutableStateOf(if (current > 0) "%.1f".format(current) else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Текущий сахар") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                suffix = { Text("ммоль/л") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val v = text.replace(',', '.').toDoubleOrNull()
                if (v != null && v > 0) onConfirm(v)
            }) { Text("ОК") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
fun SaveMealDialog(
    state: CalculatorUiState,
    onDismiss: () -> Unit,
    onSave: (notes: String, sendNs: Boolean) -> Unit
) {
    var notes by remember { mutableStateOf("") }
    var sendNs by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Записать приём") },
        text = {
            Column {
                InfoRow("Углеводы", "%.1f г".format(state.totalCarbs))
                InfoRow("ХЕ", "%.1f".format(state.breadUnits))
                InfoRow("Калории", "%.0f ккал".format(state.totalCalories))
                if (state.currentGlucose > 0)
                    InfoRow("Сахар", "%.1f ммоль/л".format(state.currentGlucose))
                InfoRow("Доза инсулина", "%.2f ед".format(state.totalDose))
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Примечание") },
                    placeholder = { Text("Например: завтрак") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                if (state.settings.nsEnabled) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = sendNs, onCheckedChange = { sendNs = it })
                        Text("Отправить в NightScout")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(notes.trim(), sendNs) }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.outline, fontSize = 13.sp)
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

enum class ComponentDialogType { PRODUCT, DISH }
