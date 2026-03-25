package com.glucoplan.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glucoplan.app.core.NsResult
import com.glucoplan.app.domain.model.AppSettings
import com.glucoplan.app.core.logging.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToPans: () -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    if (state.loading) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val s = state.settings

    var carbsPerXe  by remember(s.carbsPerXe)         { mutableStateOf(s.carbsPerXe.toString()) }
    var carbCoeff   by remember(s.carbCoefficient)     { mutableStateOf(s.carbCoefficient.toString()) }
    var sensitivity by remember(s.sensitivity)         { mutableStateOf(s.sensitivity.toString()) }
    var targetMin   by remember(s.targetGlucoseMin)    { mutableStateOf(s.targetGlucoseMin.toString()) }
    var target      by remember(s.targetGlucose)       { mutableStateOf(s.targetGlucose.toString()) }
    var targetMax   by remember(s.targetGlucoseMax)    { mutableStateOf(s.targetGlucoseMax.toString()) }
    var insulinStep by remember(s.insulinStep)         { mutableStateOf(s.insulinStep.toString()) }
    var basalDose   by remember(s.basalDose)           { mutableStateOf(s.basalDose.toString()) }
    var nsUrl       by remember(s.nsUrl)               { mutableStateOf(s.nsUrl) }
    var nsSecret    by remember(s.nsApiSecret)         { mutableStateOf(s.nsApiSecret) }
    var showSecret  by remember                        { mutableStateOf(false) }

    fun buildSettings() = s.copy(
        carbsPerXe       = carbsPerXe.toDoubleOrNull()  ?: s.carbsPerXe,
        carbCoefficient  = carbCoeff.toDoubleOrNull()   ?: s.carbCoefficient,
        sensitivity      = sensitivity.toDoubleOrNull() ?: s.sensitivity,
        targetGlucoseMin = targetMin.toDoubleOrNull()   ?: s.targetGlucoseMin,
        targetGlucose    = target.toDoubleOrNull()      ?: s.targetGlucose,
        targetGlucoseMax = targetMax.toDoubleOrNull()   ?: s.targetGlucoseMax,
        insulinStep      = insulinStep.toDoubleOrNull() ?: s.insulinStep,
        basalDose        = basalDose.toDoubleOrNull()   ?: s.basalDose,
        nsUrl            = nsUrl.trim(),
        nsApiSecret      = nsSecret
    )

    DisposableEffect(Unit) {
        onDispose { viewModel.save(buildSettings()) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                actions = {
                    IconButton(onClick = { viewModel.save(buildSettings()) }) {
                        Icon(Icons.Default.Save, "Сохранить")
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
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SectionHeader("Углеводы и ХЕ")
            BlurSaveField("1 ХЕ = ... г углеводов", carbsPerXe, { carbsPerXe = it }) { viewModel.save(buildSettings()) }

            SectionHeader("Инсулин")
            BlurSaveField("Ед на 1 ХЕ", carbCoeff, { carbCoeff = it }) { viewModel.save(buildSettings()) }
            BlurSaveField("1 ед снижает сахар на (ммоль/л)", sensitivity, { sensitivity = it }) { viewModel.save(buildSettings()) }
            BlurSaveField("Шаг ручки (ед)", insulinStep, { insulinStep = it }) { viewModel.save(buildSettings()) }
            Spacer(Modifier.height(4.dp))
            SettingsDropdown(
                label = "Тип короткого инсулина",
                options = state.insulinOptions,
                selected = s.insulinType,
                onSelect = { viewModel.save(s.copy(insulinType = it)) }
            )

            SectionHeader("Целевой уровень сахара (ммоль/л)")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BlurSaveField("Минимум", targetMin, { targetMin = it }, Modifier.weight(1f)) { viewModel.save(buildSettings()) }
                BlurSaveField("Цель", target, { target = it }, Modifier.weight(1f)) { viewModel.save(buildSettings()) }
                BlurSaveField("Максимум", targetMax, { targetMax = it }, Modifier.weight(1f)) { viewModel.save(buildSettings()) }
            }

            SectionHeader("Базальный инсулин")
            SettingsDropdown(
                label = "Тип базального",
                options = state.basalOptions,
                selected = s.basalType,
                onSelect = { viewModel.save(buildSettings().copy(basalType = it)) }
            )
            if (s.basalType != "none") {
                BlurSaveField("Суточная доза (ед)", basalDose, { basalDose = it }) { viewModel.save(buildSettings()) }
                var showTimePicker by remember { mutableStateOf(false) }
                ListItem(
                    headlineContent = { Text("Время укола") },
                    trailingContent = { Text(s.basalTime, fontSize = 16.sp) },
                    modifier = Modifier.clickable { showTimePicker = true }
                )
                if (showTimePicker) {
                    val parts = s.basalTime.split(":")
                    val tpState = rememberTimePickerState(
                        initialHour = parts[0].toIntOrNull() ?: 22,
                        initialMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0
                    )
                    AlertDialog(
                        onDismissRequest = { showTimePicker = false },
                        title = { Text("Время укола") },
                        text = { TimePicker(state = tpState) },
                        confirmButton = {
                            TextButton(onClick = {
                                val time = "%02d:%02d".format(tpState.hour, tpState.minute)
                                viewModel.save(buildSettings().copy(basalTime = time))
                                showTimePicker = false
                            }) { Text("ОК") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showTimePicker = false }) { Text("Отмена") }
                        }
                    )
                }
            }

            SectionHeader("NightScout / CGM")
            ListItem(
                headlineContent = { Text("Использовать NightScout") },
                trailingContent = {
                    Switch(checked = s.nsEnabled, onCheckedChange = { viewModel.save(buildSettings().copy(nsEnabled = it)) })
                },
                modifier = Modifier.clickable { viewModel.save(buildSettings().copy(nsEnabled = !s.nsEnabled)) }
            )
            if (s.nsEnabled) {
                //https://j89028552.nightscout-jino.ru
                //xj5kJyVv9n8F
                BlurSaveField("URL сервера", nsUrl, { nsUrl = it }, placeholder = "https://j89028552.nightscout-jino.ru") { viewModel.save(buildSettings()) }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = nsSecret,
                    onValueChange = { nsSecret = it },
                    label = { Text("API Secret") },
                    visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showSecret = !showSecret }) {
                            Icon(if (showSecret) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                        .onFocusChanged { if (!it.isFocused) viewModel.save(buildSettings()) },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = {
                        viewModel.save(buildSettings())
                        viewModel.checkNightscout(nsUrl.trim(), nsSecret)
                    }) {
                        if (state.nsChecking) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Проверить соединение")
                        }
                    }
                    when (val result = state.nsCheckResult) {
                        is NsResult.Success -> {
                            Spacer(Modifier.width(12.dp))
                            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF43A047))
                            Spacer(Modifier.width(4.dp))
                            Text("Успешно", color = Color(0xFF43A047))
                        }
                        is NsResult.Error -> {
                            Spacer(Modifier.width(12.dp))
                            Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(4.dp))
                            Text(result.message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        }
                        null -> {}
                    }
                }
            }

            SectionHeader("Кастрюли")
            ListItem(
                headlineContent = { Text("Управление кастрюлями") },
                trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                modifier = Modifier.clickable { onNavigateToPans() }
            )

            SectionHeader("О программе")
            ListItem(headlineContent = { Text("Версия") }, trailingContent = { Text(BuildConfig.VERSION_NAME) })
            ListItem(
                headlineContent = { Text("GitHub") },
                trailingContent = { Icon(Icons.Default.OpenInNew, null, Modifier.size(18.dp)) },
                modifier = Modifier.clickable { uriHandler.openUri("https://github.com/GlucoPlan/GlucoPlan-Android") }
            )
            ListItem(
                headlineContent = { Text("Telegram-канал") },
                trailingContent = { Icon(Icons.Default.OpenInNew, null, Modifier.size(18.dp)) },
                modifier = Modifier.clickable { uriHandler.openUri("https://t.me/GlucoPlan") }
            )

            Spacer(Modifier.height(16.dp))
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                Text(
                    "Приложение является вспомогательным инструментом и не заменяет рекомендации " +
                    "лечащего врача. Все коэффициенты подбираются совместно с эндокринологом.",
                    modifier = Modifier.padding(12.dp),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun BlurSaveField(
    label: String, value: String, onValue: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    placeholder: String? = null,
    onBlur: () -> Unit
) {
    OutlinedTextField(
        value = value, onValueChange = onValue,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        modifier = modifier.onFocusChanged { if (!it.isFocused) onBlur() },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDropdown(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second ?: selected
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedLabel, onValueChange = {},
            readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (key, lbl) ->
                DropdownMenuItem(text = { Text(lbl) }, onClick = { onSelect(key); expanded = false })
            }
        }
    }
}