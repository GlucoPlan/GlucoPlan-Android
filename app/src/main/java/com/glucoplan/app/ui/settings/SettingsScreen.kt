package com.glucoplan.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glucoplan.app.core.NsResult
import com.glucoplan.app.core.logging.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToPans: () -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    if (state.loading) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val s = state.settings

    var carbsPerXe       by remember(s.carbsPerXe)       { mutableStateOf(s.carbsPerXe.toString()) }
    var carbCoeff        by remember(s.carbCoefficient)   { mutableStateOf(s.carbCoefficient.toString()) }
    var sensitivity      by remember(s.sensitivity)       { mutableStateOf(s.sensitivity.toString()) }
    var targetMin        by remember(s.targetGlucoseMin)  { mutableStateOf(s.targetGlucoseMin.toString()) }
    var target           by remember(s.targetGlucose)     { mutableStateOf(s.targetGlucose.toString()) }
    var targetMax        by remember(s.targetGlucoseMax)  { mutableStateOf(s.targetGlucoseMax.toString()) }
    var insulinStep      by remember(s.insulinStep)       { mutableStateOf(s.insulinStep.toString()) }
    var basalDose        by remember(s.basalDose)         { mutableStateOf(s.basalDose.toString()) }
    var nsUrl            by remember(s.nsUrl)             { mutableStateOf(s.nsUrl) }
    var nsSecret         by remember(s.nsApiSecret)       { mutableStateOf(s.nsApiSecret) }
    var nsEnabled        by remember(s.nsEnabled)         { mutableStateOf(s.nsEnabled) }
    var showSecret       by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }

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
        nsApiSecret      = nsSecret,
        nsEnabled        = nsEnabled
    )

    DisposableEffect(Unit) {
        onDispose { viewModel.save(buildSettings()) }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.checkForUpdates() }

    LaunchedEffect(state.nsSyncResult) {
        state.nsSyncResult?.let { result ->
            val msg = when (result) {
                is NsSyncResult.UploadSuccess   -> "✓ Настройки сохранены в Nightscout"
                is NsSyncResult.DownloadSuccess -> "✓ Настройки загружены из Nightscout"
                is NsSyncResult.Error           -> "Ошибка: ${result.message}"
            }
            snackbarHostState.showSnackbar(msg)
            viewModel.clearNsSyncResult()
        }
    }

    // ── Диалог обновления ─────────────────────────────────────────────────────
    if (showUpdateDialog && state.latestVersion != null) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { Text("Доступно обновление") },
            text = {
                Column {
                    Text("Текущая версия: ${BuildConfig.VERSION_NAME}")
                    Text("Новая версия: ${state.latestVersion}")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Скачать и установить GlucoPlan ${state.latestVersion}?",
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 13.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showUpdateDialog = false
                    state.latestApkUrl?.let { url ->
                        downloadAndInstallApk(context, url, state.latestVersion!!)
                    }
                }) { Text("Обновить") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showUpdateDialog = false
                    viewModel.clearUpdateState()
                }) { Text("Не сейчас") }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
            // ═══════════════════════════════════════════════════════
            // БЛОК 1 — NIGHTSCOUT
            // ═══════════════════════════════════════════════════════
            SectionHeader("Nightscout / CGM")

            ListItem(
                headlineContent = { Text("Использовать Nightscout") },
                trailingContent = {
                    Switch(
                        checked = nsEnabled,
                        onCheckedChange = { nsEnabled = it; viewModel.save(buildSettings()) }
                    )
                },
                modifier = Modifier.clickable {
                    nsEnabled = !nsEnabled; viewModel.save(buildSettings())
                }
            )

            AnimatedVisibility(visible = nsEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BlurSaveField(
                        label = "URL сервера",
                        value = nsUrl,
                        onValue = { nsUrl = it },
                        placeholder = "https://my.nightscout.io",
                        keyboardType = KeyboardType.Uri
                    ) { viewModel.save(buildSettings()) }

                    OutlinedTextField(
                        value = nsSecret,
                        onValueChange = { nsSecret = it },
                        label = { Text("API Secret") },
                        visualTransformation = if (showSecret)
                            VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showSecret = !showSecret }) {
                                Icon(
                                    if (showSecret) Icons.Default.VisibilityOff
                                    else Icons.Default.Visibility, null
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { if (!it.isFocused) viewModel.save(buildSettings()) },
                        singleLine = true
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = {
                            viewModel.save(buildSettings())
                            viewModel.checkNightscout(nsUrl.trim(), nsSecret)
                        }) {
                            if (state.nsChecking) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Проверка...")
                            } else {
                                Icon(Icons.Default.Wifi, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
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
                                Text(
                                    result.message,
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            null -> {}
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Text(
                        "Синхронизация параметров лечения",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = {
                                viewModel.save(buildSettings())
                                viewModel.loadFromNightscout()
                            },
                            enabled = !state.nsSyncing,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (state.nsSyncing) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Download, null, Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(6.dp))
                            Text("Читать из NS", fontSize = 13.sp)
                        }

                        Button(
                            onClick = {
                                viewModel.save(buildSettings())
                                viewModel.uploadToNightscout()
                            },
                            enabled = !state.nsSyncing,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (state.nsSyncing) {
                                CircularProgressIndicator(
                                    Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Default.Upload, null, Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(6.dp))
                            Text("Сохранить в NS", fontSize = 13.sp)
                        }
                    }
                    Text(
                        "Синхронизируются: ISF, ХЕ, коэффициент, целевой сахар, тип и доза инсулина",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        lineHeight = 16.sp
                    )
                }
            }

            // ═══════════════════════════════════════════════════════
            // БЛОК 2 — ПАРАМЕТРЫ ЛЕЧЕНИЯ
            // ═══════════════════════════════════════════════════════
            SectionHeader("Параметры лечения")
            Text(
                "Эти настройки синхронизируются с Nightscout",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            SectionSubHeader("Углеводы и ХЕ")
            BlurSaveField("1 ХЕ = ... г углеводов", carbsPerXe, { carbsPerXe = it }) {
                viewModel.save(buildSettings())
            }

            SectionSubHeader("Инсулин")
            BlurSaveField("Ед на 1 ХЕ", carbCoeff, { carbCoeff = it }) {
                viewModel.save(buildSettings())
            }
            BlurSaveField("1 ед снижает сахар на (ммоль/л)", sensitivity, { sensitivity = it }) {
                viewModel.save(buildSettings())
            }
            Spacer(Modifier.height(4.dp))
            SettingsDropdown(
                label = "Тип короткого инсулина",
                options = state.insulinOptions,
                selected = s.insulinType,
                onSelect = { viewModel.save(buildSettings().copy(insulinType = it)) }
            )

            SectionSubHeader("Целевой сахар (ммоль/л)")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BlurSaveField("Мин", targetMin, { targetMin = it }, Modifier.weight(1f)) {
                    viewModel.save(buildSettings())
                }
                BlurSaveField("Цель", target, { target = it }, Modifier.weight(1f)) {
                    viewModel.save(buildSettings())
                }
                BlurSaveField("Макс", targetMax, { targetMax = it }, Modifier.weight(1f)) {
                    viewModel.save(buildSettings())
                }
            }

            SectionSubHeader("Базальный инсулин")
            SettingsDropdown(
                label = "Тип базального",
                options = state.basalOptions,
                selected = s.basalType,
                onSelect = { viewModel.save(buildSettings().copy(basalType = it)) }
            )
            if (s.basalType != "none") {
                BlurSaveField("Суточная доза (ед)", basalDose, { basalDose = it }) {
                    viewModel.save(buildSettings())
                }
                var showTimePicker by remember { mutableStateOf(false) }
                ListItem(
                    headlineContent = { Text("Время укола") },
                    trailingContent = { Text(s.basalTime, fontSize = 16.sp) },
                    modifier = Modifier.clickable { showTimePicker = true }
                )
                if (showTimePicker) {
                    val parts = s.basalTime.split(":")
                    val tpState = rememberTimePickerState(
                        initialHour   = parts[0].toIntOrNull() ?: 22,
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

            // ═══════════════════════════════════════════════════════
            // БЛОК 3 — НАСТРОЙКИ ПРИЛОЖЕНИЯ
            // ═══════════════════════════════════════════════════════
            SectionHeader("Настройки приложения")
            Text(
                "Эти настройки хранятся только на устройстве",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            BlurSaveField("Шаг ручки (ед)", insulinStep, { insulinStep = it }) {
                viewModel.save(buildSettings())
            }

            Spacer(Modifier.height(8.dp))
            SectionSubHeader("Кастрюли")
            ListItem(
                headlineContent = { Text("Управление кастрюлями") },
                trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                modifier = Modifier.clickable { onNavigateToPans() }
            )

            // ═══════════════════════════════════════════════════════
            // О ПРОГРАММЕ
            // ═══════════════════════════════════════════════════════
            SectionHeader("О программе")
            ListItem(
                headlineContent = { Text("Версия") },
                supportingContent = if (state.updateAvailable) {
                    {
                        Text(
                            "Доступна ${state.latestVersion} — нажмите для обновления",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp
                        )
                    }
                } else null,
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        when {
                            state.updateChecking -> {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(BuildConfig.VERSION_NAME)
                            }
                            state.updateAvailable -> {
                                Text(BuildConfig.VERSION_NAME, color = MaterialTheme.colorScheme.outline)
                                Spacer(Modifier.width(8.dp))
                                Icon(
                                    Icons.Default.SystemUpdate, null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            else -> Text(BuildConfig.VERSION_NAME)
                        }
                    }
                },
                modifier = if (state.updateAvailable)
                    Modifier.clickable { showUpdateDialog = true }
                else
                    Modifier
            )
            ListItem(
                headlineContent  = { Text("GitHub") },
                trailingContent  = { Icon(Icons.Default.OpenInNew, null, Modifier.size(18.dp)) },
                modifier = Modifier.clickable {
                    uriHandler.openUri("https://github.com/GlucoPlan/GlucoPlan-Android")
                }
            )
            ListItem(
                headlineContent  = { Text("Telegram-канал") },
                trailingContent  = { Icon(Icons.Default.OpenInNew, null, Modifier.size(18.dp)) },
                modifier = Modifier.clickable {
                    uriHandler.openUri("https://t.me/glucoplan")
                }
            )

            Spacer(Modifier.height(16.dp))
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    "Приложение является вспомогательным инструментом и не заменяет " +
                    "рекомендации лечащего врача. Все коэффициенты подбираются совместно " +
                    "с эндокринологом. Вся ответственность за последствия медицинского " +
                            "применения приложения лежит только на Вас.",
                    modifier = Modifier.padding(12.dp),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ─── Вспомогательные компоненты ───────────────────────────────────────────────

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 2.dp)
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
}

@Composable
private fun SectionSubHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun BlurSaveField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Decimal,
    onBlur: () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        modifier = modifier.onFocusChanged { if (!it.isFocused) onBlur() },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
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
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (key, lbl) ->
                DropdownMenuItem(
                    text = { Text(lbl) },
                    onClick = { onSelect(key); expanded = false }
                )
            }
        }
    }
}

// ─── Скачивание и установка APK ───────────────────────────────────────────────

private fun downloadAndInstallApk(context: android.content.Context, url: String, version: String) {
    val apkName = "GlucoPlan_$version.apk"
    val destDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
        ?: return
    val destFile = java.io.File(destDir, apkName)

    val request = android.app.DownloadManager.Request(android.net.Uri.parse(url)).apply {
        setTitle("GlucoPlan $version")
        setDescription("Загрузка обновления...")
        setNotificationVisibility(
            android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
        )
        setDestinationUri(android.net.Uri.fromFile(destFile))
        setMimeType("application/vnd.android.package-archive")
    }

    val dm = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE)
            as android.app.DownloadManager
    val downloadId = dm.enqueue(request)

    val receiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(ctx: android.content.Context, intent: android.content.Intent) {
            val id = intent.getLongExtra(android.app.DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (id != downloadId) return
            ctx.unregisterReceiver(this)

            val query = android.app.DownloadManager.Query().setFilterById(downloadId)
            val success = dm.query(query)?.use { c ->
                c.moveToFirst() && c.getInt(
                    c.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_STATUS)
                ) == android.app.DownloadManager.STATUS_SUCCESSFUL
            } ?: false

            if (!success) {
                android.widget.Toast.makeText(
                    ctx, "Ошибка загрузки APK", android.widget.Toast.LENGTH_LONG
                ).show()
                return
            }

            try {
                val apkUri = androidx.core.content.FileProvider.getUriForFile(
                    ctx, "${ctx.packageName}.fileprovider", destFile
                )
                val installIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(installIntent)
            } catch (e: Exception) {
                android.widget.Toast.makeText(
                    ctx, "Не удалось открыть установщик: ${e.message}", android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    context.registerReceiver(
        receiver,
        android.content.IntentFilter(android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE),
        android.content.Context.RECEIVER_NOT_EXPORTED
    )
}
