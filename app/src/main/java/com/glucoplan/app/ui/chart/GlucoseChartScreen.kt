package com.glucoplan.app.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter


// Цвета для зон сахара (совпадают с Theme.kt)
private val ColorHypo    = Color(0xFFE53935)   // < 3.9  — красный
private val ColorLow     = Color(0xFFFF9800)   // 3.9–5.0 — оранжевый
private val ColorInRange = Color(0xFF43A047)   // 5.0–10.0 — зелёный
private val ColorHigh    = Color(0xFFE65100)   // 10.0–14  — тёмно-оранжевый
private val ColorHyper   = Color(0xFFB71C1C)   // > 14     — тёмно-красный
private val ColorMeal    = Color(0xFF2196F3)   // еда
private val ColorInsulin = Color(0xFF9C27B0)   // инсулин
private val ColorManual  = Color(0xFF795548)   // глюкометр
private val ColorGrid    = Color(0x22000000)

private fun glucoseColor(g: Double) = when {
    g < 3.9   -> ColorHypo
    g < 5.0   -> ColorLow
    g <= 10.0 -> ColorInRange
    g <= 14.0 -> ColorHigh
    else      -> ColorHyper
}

private val timeFmt  = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
private val dateFmt  = DateTimeFormatter.ofPattern("dd MMM").withZone(ZoneId.systemDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlucoseChartScreen(
    viewModel: GlucoseChartViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val textMeasurer = rememberTextMeasurer()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("График глюкозы") },
                actions = {
                    IconButton(onClick = { viewModel.goToNow() }) {
                        Icon(Icons.Default.AccessTime, "Сейчас")
                    }
                    IconButton(onClick = { viewModel.load() }) {
                        Icon(Icons.Default.Refresh, "Обновить")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Переключатель масштаба ────────────────────────────────────────
            WindowSelector(
                current = state.windowHours,
                onSelect = { viewModel.setWindowHours(it) }
            )

            when {
                state.loading -> {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text("Загрузка данных из Nightscout…",
                                color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
                state.error != null -> {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.size(120.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.WifiOff, null,
                                        modifier = Modifier.size(56.dp),
                                        tint = MaterialTheme.colorScheme.onErrorContainer)
                                }
                            }
                            Spacer(Modifier.height(24.dp))
                            Text("Нет соединения с Nightscout",
                                style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Text(state.error!!, color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { viewModel.load() }) { Text("Повторить") }
                        }
                    }
                }
                state.points.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(120.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Timeline, null,
                                        modifier = Modifier.size(56.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }
                            Spacer(Modifier.height(24.dp))
                            Text("Нет данных CGM", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Text("Данные появятся после синхронизации с Nightscout",
                                color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
                else -> {
                    // ── Основной график ───────────────────────────────────────
                    GlucoseChart(
                        state = state,
                        textMeasurer = textMeasurer,
                        onTouch = { viewModel.onChartTouch(it) },
                        onTouchEnd = { /* keep tooltip */ },
                        onHorizontalDrag = { viewModel.shiftWindow(it) },
                        onPinchZoom = { viewModel.onPinchZoom(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                    )

                    // ── Тултип ────────────────────────────────────────────────
                    TooltipPanel(tooltip = state.tooltip, settings = state.settings)

                    // ── Легенда ───────────────────────────────────────────────
                    Legend()

                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

// ─── Переключатель ширины окна ────────────────────────────────────────────────

@Composable
private fun WindowSelector(current: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(6 to "6ч", 12 to "12ч", 24 to "24ч", 48 to "48ч").forEach { (h, label) ->
            FilterChip(
                selected = current == h,
                onClick = { onSelect(h) },
                label = { Text(label, fontSize = 13.sp) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ─── Тултип ───────────────────────────────────────────────────────────────────

@Composable
private fun TooltipPanel(tooltip: ChartTooltip?, settings: com.glucoplan.app.domain.model.AppSettings) {
    val height = 72.dp
    Surface(
        tonalElevation = 3.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .height(height)
    ) {
        if (tooltip == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("Коснитесь графика для деталей",
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 13.sp)
            }
            return@Surface
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Время
            Column {
                Text(timeFmt.format(tooltip.time),
                    fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(dateFmt.format(tooltip.time),
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
            }

            // Сахар
            tooltip.glucose?.let { g ->
                Column {
                    Text("%.1f".format(g),
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        color = glucoseColor(g))
                    Text("ммоль/л", fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.outline)
                }
            }

            Spacer(Modifier.weight(1f))

            // Ближайшее событие
            tooltip.nearestEvent?.let { ev ->
                when (ev) {
                    is ChartEvent.Meal -> Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).background(ColorMeal, CircleShape))
                            Spacer(Modifier.width(4.dp))
                            Text("Еда", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        }
                        // Углеводы + ГИ категория
                        val giText = when (ev.giCategory) {
                            "low"    -> " · ГИ↓"
                            "medium" -> " · ГИ~"
                            "high"   -> " · ГИ↑"
                            else     -> ""
                        }
                        Text("УВ: %.0f г$giText".format(ev.carbs),
                            fontWeight = FontWeight.Medium, fontSize = 13.sp)
                        // Белки и жиры
                        if (ev.proteins > 0 || ev.fats > 0) {
                            val bjuParts = mutableListOf<String>()
                            if (ev.proteins > 0) bjuParts.add("Б: %.0f".format(ev.proteins))
                            if (ev.fats > 0) bjuParts.add("Ж: %.0f".format(ev.fats))
                            Text(bjuParts.joinToString("  "),
                                fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        }
                        if (ev.insulin > 0)
                            Text("%.1f ед".format(ev.insulin),
                                fontSize = 12.sp, color = ColorInsulin)
                    }
                    is ChartEvent.Injection -> Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).background(ColorInsulin, CircleShape))
                            Spacer(Modifier.width(4.dp))
                            Text("Инсулин", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        }
                        Text("%.1f ед".format(ev.dose),
                            fontWeight = FontWeight.Medium, fontSize = 13.sp)
                        Text(ev.insulinType, fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}

// ─── Легенда ──────────────────────────────────────────────────────────────────

@Composable
private fun Legend() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LegendDot(ColorInRange, "Норма")
        LegendDot(ColorHypo,    "Гипо/Гипер")
        LegendDot(ColorMeal,    "Еда")
        LegendDot(ColorInsulin, "Инсулин")
        LegendDot(ColorManual,  "Глюкометр ●")
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(3.dp))
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
    }
}

// ─── Основной Canvas-график ───────────────────────────────────────────────────

@Composable
private fun GlucoseChart(
    state: GlucoseChartUiState,
    textMeasurer: TextMeasurer,
    onTouch: (Float) -> Unit,
    onTouchEnd: () -> Unit,
    onHorizontalDrag: (Double) -> Unit,
    onPinchZoom: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    // Накапливаем drag delta
    // Отступы для осей
    val padLeft   = 48f   // px, для оси Y
    val padRight  = 8f
    val padTop    = 12f
    val padBottom = 28f   // px, для оси X

    // Зоны гипо/гипер для фона
    val targetMin = state.settings.targetGlucoseMin.toFloat()
    val targetMax = state.settings.targetGlucoseMax.toFloat()

    Canvas(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            // Один палец: тап или drag = двигаем точку наблюдения (тултип)
            .pointerInput(state.windowStart, state.windowEnd) {
                val chartWidth = size.width - padLeft - padRight
                detectTapGestures { offset ->
                    val frac = ((offset.x - padLeft) / chartWidth).coerceIn(0f, 1f)
                    onTouch(frac)
                }
            }
            .pointerInput(state.windowStart, state.windowEnd) {
                val chartWidth = size.width - padLeft - padRight
                // Один палец — только тултип, без скролла
                detectDragGestures(
                    onDragStart = { offset ->
                        val frac = ((offset.x - padLeft) / chartWidth).coerceIn(0f, 1f)
                        onTouch(frac)
                    }
                ) { change, _ ->
                    // dragAmount игнорируем — не скроллим, только обновляем тултип
                    change.consume()
                    val frac = ((change.position.x - padLeft) / chartWidth).coerceIn(0f, 1f)
                    onTouch(frac)
                }
            }
            // Два пальца: скролл влево/вправо + pinch zoom
            .pointerInput(state.windowHours, state.windowStart, state.windowEnd) {
                val chartWidth = size.width - padLeft - padRight
                val hoursPerPx = state.windowHours.toDouble() / chartWidth
                detectTransformGestures { _, pan, zoom, _ ->
                    // Скролл: pan.x > 0 = тянем вправо = идём назад
                    if (pan.x != 0f) {
                        onHorizontalDrag(-pan.x * hoursPerPx)
                    }
                    // Zoom: scale > 1 = сводим пальцы = уменьшаем окно (zoom in)
                    //        scale < 1 = раздвигаем = увеличиваем окно (zoom out)
                    if (zoom != 1f) {
                        onPinchZoom(zoom)
                    }
                }
            }
    ) {
        val w = size.width
        val h = size.height
        val chartW = w - padLeft - padRight
        val chartH = h - padTop - padBottom

        val yRange = (state.yMax - state.yMin).toFloat()
        val windowStartMs = state.windowStart.toEpochMilli().toFloat()
        val windowEndMs   = state.windowEnd.toEpochMilli().toFloat()
        val timeRange     = windowEndMs - windowStartMs

        fun timeToX(t: Instant): Float {
            val frac = (t.toEpochMilli() - windowStartMs) / timeRange
            return padLeft + frac * chartW
        }

        fun glucoseToY(g: Double): Float {
            val frac = 1f - ((g.toFloat() - state.yMin.toFloat()) / yRange)
            return padTop + frac * chartH
        }

        // ── 1. Фон зон ────────────────────────────────────────────────────────

        // Гипо зона (< targetMin)
        val hypoTop = glucoseToY(targetMin.toDouble())
        drawRect(
            color = Color(0x18E53935),
            topLeft = Offset(padLeft, hypoTop),
            size = androidx.compose.ui.geometry.Size(chartW, h - padBottom - hypoTop)
        )

        // Норма зона (targetMin .. targetMax)
        val normTop    = glucoseToY(targetMax.toDouble())
        val normBottom = glucoseToY(targetMin.toDouble())
        drawRect(
            color = Color(0x1243A047),
            topLeft = Offset(padLeft, normTop),
            size = androidx.compose.ui.geometry.Size(chartW, normBottom - normTop)
        )

        // Горизонтальные линии цели
        listOf(targetMin.toDouble(), targetMax.toDouble()).forEach { g ->
            drawLine(
                color = ColorInRange.copy(alpha = 0.4f),
                start = Offset(padLeft, glucoseToY(g)),
                end   = Offset(padLeft + chartW, glucoseToY(g)),
                strokeWidth = 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
            )
        }

        // ── 2. Сетка и ось Y ──────────────────────────────────────────────────

        val yTicks = generateYTicks(state.yMin, state.yMax)
        yTicks.forEach { gVal ->
            val y = glucoseToY(gVal)
            if (y < padTop || y > h - padBottom) return@forEach

            // Горизонтальная линия сетки
            drawLine(
                color = ColorGrid,
                start = Offset(padLeft, y),
                end   = Offset(padLeft + chartW, y),
                strokeWidth = 1f
            )

            // Метка
            val label = textMeasurer.measure(
                "%.0f".format(gVal),
                style = TextStyle(fontSize = 10.sp, color = Color(0xFF888888))
            )
            drawText(label, topLeft = Offset(2f, y - label.size.height / 2f))
        }

        // Вертикальная линия оси Y
        drawLine(
            color = Color(0x44000000),
            start = Offset(padLeft, padTop),
            end   = Offset(padLeft, h - padBottom),
            strokeWidth = 1f
        )

        // ── 3. Ось X (время) ─────────────────────────────────────────────────

        val xTicks = generateXTicks(state.windowStart, state.windowEnd, state.windowHours)
        xTicks.forEach { t ->
            val x = timeToX(t)
            if (x < padLeft || x > padLeft + chartW) return@forEach

            drawLine(
                color = ColorGrid,
                start = Offset(x, padTop),
                end   = Offset(x, h - padBottom),
                strokeWidth = 1f
            )

            val label = textMeasurer.measure(
                timeFmt.format(t),
                style = TextStyle(fontSize = 9.sp, color = Color(0xFF888888))
            )
            drawText(label, topLeft = Offset(x - label.size.width / 2f, h - padBottom + 4f))
        }

        // ── 4. Кривая CGM ─────────────────────────────────────────────────────

        val pts = state.visiblePoints
        if (pts.size >= 2) {
            // Рисуем сегменты с цветом по значению сахара
            for (i in 0 until pts.size - 1) {
                val p1 = pts[i]
                val p2 = pts[i + 1]
                // Пропускаем разрывы > 20 минут (потеря сигнала)
                if (p2.time.epochSecond - p1.time.epochSecond > 1200) continue
                drawLine(
                    color = glucoseColor(p1.glucose),
                    start = Offset(timeToX(p1.time), glucoseToY(p1.glucose)),
                    end   = Offset(timeToX(p2.time), glucoseToY(p2.glucose)),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )
            }

            // Точки
            pts.forEach { p ->
                val x = timeToX(p.time)
                val y = glucoseToY(p.glucose)
                val color = if (p.isManual) ColorManual else glucoseColor(p.glucose)
                val radius = if (p.isManual) 5f else 3f
                drawCircle(color = color, radius = radius, center = Offset(x, y))
                if (p.isManual) {
                    drawCircle(color = Color.White, radius = 2f, center = Offset(x, y))
                }
            }
        }

        // ── 5. Маркеры событий ────────────────────────────────────────────────

        state.visibleEvents.forEach { ev ->
            val x = timeToX(ev.time)
            if (x < padLeft || x > padLeft + chartW) return@forEach

            when (ev) {
                is ChartEvent.Meal -> {
                    // Треугольник вверх — синий
                    val size = 10f
                    val yBase = h - padBottom - 4f
                    drawPath(
                        path = Path().apply {
                            moveTo(x, yBase - size)
                            lineTo(x - size * 0.6f, yBase)
                            lineTo(x + size * 0.6f, yBase)
                            close()
                        },
                        color = ColorMeal
                    )
                    // Вертикальная линия от треугольника вверх
                    drawLine(
                        color = ColorMeal.copy(alpha = 0.5f),
                        start = Offset(x, padTop),
                        end   = Offset(x, yBase - size),
                        strokeWidth = 1f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
                    )
                }
                is ChartEvent.Injection -> {
                    // Ромб — фиолетовый
                    val size = 8f
                    val yCenter = h - padBottom - 18f
                    drawPath(
                        path = Path().apply {
                            moveTo(x, yCenter - size)
                            lineTo(x + size * 0.7f, yCenter)
                            lineTo(x, yCenter + size)
                            lineTo(x - size * 0.7f, yCenter)
                            close()
                        },
                        color = ColorInsulin
                    )
                    drawLine(
                        color = ColorInsulin.copy(alpha = 0.4f),
                        start = Offset(x, padTop),
                        end   = Offset(x, yCenter - size),
                        strokeWidth = 1f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
                    )
                }
            }
        }

        // ── 6. Пунктирный крест + метки на осях ─────────────────────────────

        state.tooltip?.let { tip ->
            val x = timeToX(tip.time)
            val y = tip.glucose?.let { glucoseToY(it) }
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 5f))
            val crossColor = Color(0xFFFFFFFF)  // белый поверх тёмного фона
            val crossShadow = Color(0x99000000) // тень для контраста

            // Метка времени на оси X (белый фон + текст)
            val timeLabel = textMeasurer.measure(
                timeFmt.format(tip.time),
                style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    color = Color.White)
            )
            val timeLabelW = timeLabel.size.width.toFloat() + 8f
            val timeLabelH = timeLabel.size.height.toFloat() + 4f
            val timeLabelX = (x - timeLabelW / 2f).coerceIn(padLeft, padLeft + chartW - timeLabelW)
            val timeLabelY = h - padBottom + 2f
            drawRoundRect(
                color = Color(0xDD1565C0),
                topLeft = Offset(timeLabelX, timeLabelY),
                size = androidx.compose.ui.geometry.Size(timeLabelW, timeLabelH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f)
            )
            drawText(timeLabel, topLeft = Offset(timeLabelX + 4f, timeLabelY + 2f))

            if (y != null) {
                // ── Горизонтальная линия от точки влево до оси Y ─────────────
                drawLine(
                    color = crossShadow,
                    start = Offset(padLeft, y),
                    end   = Offset(x, y),
                    strokeWidth = 4f,
                    pathEffect = dashEffect
                )
                drawLine(
                    color = crossColor,
                    start = Offset(padLeft, y),
                    end   = Offset(x, y),
                    strokeWidth = 2f,
                    pathEffect = dashEffect
                )

                // ── Вертикальная линия от точки ВНИЗ до оси X ────────────────
                drawLine(
                    color = crossShadow,
                    start = Offset(x, y),
                    end   = Offset(x, h - padBottom),
                    strokeWidth = 4f,
                    pathEffect = dashEffect
                )
                drawLine(
                    color = crossColor,
                    start = Offset(x, y),
                    end   = Offset(x, h - padBottom),
                    strokeWidth = 2f,
                    pathEffect = dashEffect
                )

                // Метка значения на оси Y (цветной фон)
                val glColor = glucoseColor(tip.glucose!!)
                val glLabel = textMeasurer.measure(
                    "%.1f".format(tip.glucose),
                    style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        color = Color.White)
                )
                val glLabelW = glLabel.size.width.toFloat() + 8f
                val glLabelH = glLabel.size.height.toFloat() + 4f
                val glLabelY = (y - glLabelH / 2f).coerceIn(padTop, h - padBottom - glLabelH)
                drawRoundRect(
                    color = glColor,
                    topLeft = Offset(0f, glLabelY),
                    size = androidx.compose.ui.geometry.Size(glLabelW, glLabelH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f)
                )
                drawText(glLabel, topLeft = Offset(4f, glLabelY + 2f))

                // ── Точка на кривой ──────────────────────────────────────────
                drawCircle(color = Color.White, radius = 8f, center = Offset(x, y))
                drawCircle(color = glColor, radius = 6f, center = Offset(x, y))
                drawCircle(color = Color.White, radius = 2.5f, center = Offset(x, y))
            }
        }
    }
}

// ─── Вспомогательные функции ──────────────────────────────────────────────────

private fun generateYTicks(yMin: Double, yMax: Double): List<Double> {
    val step = when {
        yMax - yMin > 20 -> 4.0
        yMax - yMin > 10 -> 2.0
        else -> 1.0
    }
    val ticks = mutableListOf<Double>()
    var v = kotlin.math.ceil(yMin / step) * step
    while (v <= yMax) {
        ticks.add(v)
        v += step
    }
    return ticks
}

private fun generateXTicks(start: Instant, end: Instant, windowHours: Int): List<Instant> {
    val stepHours = when {
        windowHours <= 6  -> 1L
        windowHours <= 12 -> 2L
        windowHours <= 24 -> 4L
        else              -> 6L
    }
    val ticks = mutableListOf<Instant>()
    // Округляем до ближайшего часа
    val startEpoch = start.toEpochMilli()
    val stepMs     = stepHours * 3600_000L
    var t          = (startEpoch / stepMs + 1) * stepMs  // первый тик >= start
    while (t <= end.toEpochMilli()) {
        ticks.add(Instant.ofEpochMilli(t))
        t += stepMs
    }
    return ticks
}
