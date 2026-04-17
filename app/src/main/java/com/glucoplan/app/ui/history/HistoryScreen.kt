package com.glucoplan.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glucoplan.app.domain.model.Meal
import com.glucoplan.app.ui.calculator.CalculatorViewModel
import com.glucoplan.app.ui.theme.GlucoseColor
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = hiltViewModel(),
    calcViewModel: CalculatorViewModel = hiltViewModel(),
    onMealCopied: () -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedMeal by remember { mutableStateOf<Meal?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("История") },
                actions = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(
                            if (state.filterDate != null) Icons.Default.FilterAlt
                            else Icons.Default.FilterAltOff,
                            contentDescription = "Фильтр"
                        )
                    }
                    if (state.filterDate != null) {
                        IconButton(onClick = { viewModel.setFilter(null) }) {
                            Icon(Icons.Default.Clear, contentDescription = "Сбросить")
                        }
                    }
                    IconButton(onClick = { viewModel.load() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                    }
                }
            )
        }
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            state.meals.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.History, null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(16.dp))
                    Text("История пуста", color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { viewModel.load() }) { Text("Обновить") }
                }
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(
                    start = 8.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    end = 8.dp,
                    bottom = 8.dp
                )
            ) {
                items(state.meals, key = { it.id }) { meal ->
                    MealCard(
                        meal = meal,
                        onDelete = { viewModel.delete(meal) },
                        onTap = { selectedMeal = meal },
                        onCopyToCalc = {
                            scope.launch {
                                val comps = viewModel.buildCalcComponents(meal.id)
                                calcViewModel.loadFromHistory(comps)
                                snackbarHostState.showSnackbar("Загружено в калькулятор")
                                onMealCopied()
                            }
                        }
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { ms ->
                        val date = Instant.ofEpochMilli(ms)
                            .atZone(ZoneId.systemDefault()).toLocalDate()
                        viewModel.setFilter(date)
                    }
                    showDatePicker = false
                }) { Text("ОК") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Отмена") }
            }
        ) { DatePicker(state = datePickerState) }
    }

    selectedMeal?.let { meal ->
        MealDetailSheet(
            meal = meal,
            viewModel = viewModel,
            onDismiss = { selectedMeal = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MealCard(
    meal: Meal,
    onDelete: () -> Unit,
    onTap: () -> Unit,
    onCopyToCalc: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { v ->
            if (v == SwipeToDismissBoxValue.EndToStart) showDeleteDialog = true
            false
        }
    )
    val glucoseColor = if (meal.glucose > 0) GlucoseColor(meal.glucose, 3.9, 10.0) else null

    val dt = remember(meal.datetime) {
        try {
            val ldt = LocalDateTime.parse(
                meal.datetime,
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
            )
            ldt.format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale("ru")))
        } catch (e: DateTimeParseException) { meal.datetime }
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.error)
                    .padding(end = 16.dp),
                Alignment.CenterEnd
            ) {
                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.onError)
            }
        },
        enableDismissFromStartToEnd = false
    ) {
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onTap() }) {
            ListItem(
                headlineContent = { Text(dt) },
                supportingContent = {
                    val notes = if (meal.notes.isNotBlank()) "  ·  ${meal.notes}" else ""
                    Text("УВ: %.1f г  ·  ХЕ: %.1f$notes".format(meal.totalCarbs, meal.breadUnits))
                },
                trailingContent = {
                    Column(horizontalAlignment = Alignment.End) {
                        if (meal.glucose > 0) {
                            Text(
                                "%.1f ммоль/л".format(meal.glucose),
                                color = glucoseColor ?: MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        Text("%.1f ед".format(meal.insulinDose),
                            fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Удалить запись?") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteDialog = false }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Отмена") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MealDetailSheet(meal: Meal, viewModel: HistoryViewModel, onDismiss: () -> Unit) {
    val components by viewModel.components.collectAsStateWithLifecycle()
    LaunchedEffect(meal.id) { viewModel.loadComponents(meal.id) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Состав приёма", style = MaterialTheme.typography.titleMedium)
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        if (components.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                Text("Нет данных о составе", color = MaterialTheme.colorScheme.outline)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                items(components) { row ->
                    ListItem(
                        headlineContent = { Text(row.displayName) },
                        supportingContent = { Text("УВ: %.1f г".format(row.carbsInPortion)) },
                        trailingContent = { Text("%.0f г".format(row.servingWeight)) }
                    )
                }
            }
        }
    }
}
