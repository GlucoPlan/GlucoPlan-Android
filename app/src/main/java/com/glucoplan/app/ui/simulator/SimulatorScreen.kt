package com.glucoplan.app.ui.simulator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glucoplan.app.domain.model.Confidence
import com.glucoplan.app.domain.model.InsulinProfiles
import com.glucoplan.app.ui.calculator.CalculatorUiState
import com.glucoplan.app.ui.theme.GlucoseColor
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.core.axis.AxisPosition
import com.patrykandpatrick.vico.core.axis.formatter.AxisValueFormatter
import com.patrykandpatrick.vico.core.chart.line.LineChart
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.entryOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimulatorScreen(
    calcState: CalculatorUiState?,
    viewModel: SimulatorViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Initialize from calculator if provided
    LaunchedEffect(calcState) {
        if (calcState != null) {
            viewModel.init(
                components = calcState.components,
                insulin = calcState.totalDose,
                glucose = calcState.currentGlucose,
                insulinType = calcState.settings.insulinType,
                settings = calcState.settings
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Симулятор") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleAdvanced() }) {
                        Icon(
                            if (state.showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            "Настройки"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ─── Input Summary ───
            InputSummaryCard(state)

            // ─── Advanced Settings ───
            if (state.showAdvanced) {
                AdvancedSettingsCard(state, viewModel)
            }

            // ─── Chart ───
            if (state.combinedPoints.isNotEmpty()) {
                SimulationChart(state)
            }

            // ─── Results ───
            ResultsCard(state)

            // ─── Recommendations ───
            state.recommendation?.let { recommendation ->
                RecommendationCard(recommendation)
            }

            // ─── Legend ───
            LegendCard()
        }
    }
}

@Composable
private fun InputSummaryCard(state: SimulatorUiState) {
    OutlinedCard {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Текущий сахар", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    Text(
                        "${"%.1f".format(state.currentGlucose)} ммоль/л",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = GlucoseColor(state.currentGlucose, state.settings.targetGlucoseMin, state.settings.targetGlucoseMax)
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Инсулин", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    Text(
                        "${"%.1f".format(state.insulinDose)} ед",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        state.insulinProfile?.displayName ?: state.insulinType,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                NutrientStat("Углеводы", "${"%.0f".format(state.totalCarbs)} г", MaterialTheme.colorScheme.primary)
                NutrientStat("Белки", "${"%.0f".format(state.totalProtein)} г", MaterialTheme.colorScheme.secondary)
                NutrientStat("Жиры", "${"%.0f".format(state.totalFat)} г", MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

@Composable
private fun NutrientStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = color)
    }
}

@Composable
private fun AdvancedSettingsCard(state: SimulatorUiState, viewModel: SimulatorViewModel) {
    OutlinedCard {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Параметры инсулина", fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))

            state.insulinProfile?.let { profile ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    LabeledValue("Начало", "${"%.0f".format(profile.onsetMinutes)} мин")
                    LabeledValue("Пик", "${"%.0f".format(profile.peakMinutes)} мин")
                    LabeledValue("Длительность", "${"%.0f".format(profile.durationMinutes / 60)} ч")
                }
            }

            Spacer(Modifier.height(12.dp))

            Text("Тип инсулина", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InsulinProfiles.BOLUS_PROFILES.values.take(3).forEach { profile ->
                    FilterChip(
                        selected = state.insulinType == profile.name,
                        onClick = { viewModel.update(insulinType = profile.name) },
                        label = { Text(profile.displayName, fontSize = 11.sp) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
        Text(value, fontWeight = FontWeight.Medium, fontSize = 13.sp)
    }
}

@Composable
private fun SimulationChart(state: SimulatorUiState) {
    OutlinedCard {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Прогноз гликемии", fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))

            val modelProducer = remember { ChartEntryModelProducer() }

            // Combine all curves for display
            val combinedEntries = state.combinedPoints.map { entryOf(it.minutes.toFloat(), it.value.toFloat()) }
            val carbOnlyEntries = state.fastCarbPoints.map { 
                entryOf(it.minutes.toFloat(), it.value.toFloat()) 
            }
            val insulinOnlyEntries = state.insulinPoints.map { 
                entryOf(it.minutes.toFloat(), it.value.toFloat()) 
            }

            LaunchedEffect(state.combinedPoints) {
                modelProducer.setEntries(listOf(combinedEntries, carbOnlyEntries, insulinOnlyEntries))
            }

            val bottomAxisFormatter = AxisValueFormatter<AxisPosition.Horizontal.Bottom> { value, _ ->
                val hours = value.toInt() / 60
                val mins = value.toInt() % 60
                if (hours > 0) "${hours}ч ${mins}м" else "${mins}м"
            }

            Chart(
                chart = lineChart(
                    lines = listOf(
                        LineChart.LineSpec(lineColor = Color(0xFF43A047).toArgb()),  // Combined - green
                        LineChart.LineSpec(lineColor = Color(0xFF2196F3).toArgb()),  // Carbs - blue  
                        LineChart.LineSpec(lineColor = Color(0xFFFF5722).toArgb())   // Insulin - orange
                    )
                ),
                chartModelProducer = modelProducer,
                startAxis = rememberStartAxis(),
                bottomAxis = rememberBottomAxis(valueFormatter = bottomAxisFormatter),
                modifier = Modifier.fillMaxWidth().height(200.dp)
            )
        }
    }
}

@Composable
private fun ResultsCard(state: SimulatorUiState) {
    OutlinedCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            val peakColor = GlucoseColor(state.peakGlucose, state.settings.targetGlucoseMin, state.settings.targetGlucoseMax)
            val finalColor = GlucoseColor(state.finalGlucose, state.settings.targetGlucoseMin, state.settings.targetGlucoseMax)

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Пик", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                Text("${"%.1f".format(state.peakGlucose)}", fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = peakColor)
                Text("ммоль/л", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                Text("через ${state.minutesToPeak} мин", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Через 6ч", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                Text("${"%.1f".format(state.finalGlucose)}", fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = finalColor)
                Text("ммоль/л", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
            }

            if (state.iobState.totalIob > 0.1) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Активный инс.", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    Text("${"%.1f".format(state.iobState.totalIob)}", fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = MaterialTheme.colorScheme.tertiary)
                    Text("ед", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

@Composable
private fun RecommendationCard(
    recommendation: com.glucoplan.app.domain.model.BolusRecommendation
) {
    val cardColor = when (recommendation.confidence) {
        Confidence.HIGH -> MaterialTheme.colorScheme.primaryContainer
        Confidence.MEDIUM -> MaterialTheme.colorScheme.secondaryContainer
        Confidence.LOW -> MaterialTheme.colorScheme.tertiaryContainer
        Confidence.DO_NOT_INJECT -> MaterialTheme.colorScheme.errorContainer
    }

    OutlinedCard(
        colors = CardDefaults.outlinedCardColors(containerColor = cardColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header with timing
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = when (recommendation.confidence) {
                            Confidence.HIGH -> "✅ Рекомендация"
                            Confidence.MEDIUM -> "⚠️ Рекомендация (с оговорками)"
                            Confidence.LOW -> "⚠️ Низкая уверенность"
                            Confidence.DO_NOT_INJECT -> "🚫 Не колоть!"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Dose and timing
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Доза", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    Text("${"%.1f".format(recommendation.dose)} ед", fontWeight = FontWeight.Bold, fontSize = 24.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Когда", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    Text(recommendation.timing.description, fontWeight = FontWeight.Medium, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            }

            // Reasoning
            if (recommendation.reasoning.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(recommendation.reasoning, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Warnings
            if (recommendation.warnings.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                recommendation.warnings.forEach { warning ->
                    Text(warning, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
            }

            // Adjustments breakdown
            if (recommendation.adjustments.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text("Детали расчёта:", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                recommendation.adjustments.forEach { adj ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(adj.reason, fontSize = 12.sp)
                        Text(
                            "${if (adj.amount >= 0) "+" else ""}${"%.2f".format(adj.amount)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (adj.amount < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendCard() {
    Surface(
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LegendItem(Color(0xFF43A047), "Прогноз")
            LegendItem(Color(0xFF2196F3), "Только еда")
            LegendItem(Color(0xFFFF5722), "Только инсулин")
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp, 3.dp)
                .background(color, MaterialTheme.shapes.small)
        )
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 11.sp)
    }
}
