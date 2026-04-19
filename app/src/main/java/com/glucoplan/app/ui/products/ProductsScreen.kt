package com.glucoplan.app.ui.products

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.NoFood
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.SoupKitchen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glucoplan.app.domain.model.Product
import androidx.compose.runtime.LaunchedEffect
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(
    viewModel: ProductsViewModel = hiltViewModel(),
    onNavigateToDishes: () -> Unit = {}
) {
    val products by viewModel.filteredProducts.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val sortField by viewModel.sortField.collectAsStateWithLifecycle()
    val sortAsc by viewModel.sortAsc.collectAsStateWithLifecycle()
    val saveError by viewModel.saveError.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(saveError) {
        saveError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSaveError()
        }
    }
    val context = LocalContext.current

    var editProduct by remember { mutableStateOf<Product?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importUri by remember { mutableStateOf<Uri?>(null) }

    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importUri = it; showImportDialog = true }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Продукты") },
                actions = {
                    IconButton(onClick = onNavigateToDishes) {
                        Icon(Icons.Default.SoupKitchen, "Блюда")
                    }
                    IconButton(onClick = {
                        csvPicker.launch(arrayOf("text/csv", "text/comma-separated-values", "*/*"))
                    }) {
                        Icon(Icons.Default.Upload, "Импорт CSV")
                    }
                    IconButton(onClick = { viewModel.exportCsv(context) }) {
                        Icon(Icons.Default.Download, "Экспорт CSV")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, "Добавить")
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.setQuery(it) },
                label = { Text("Поиск продукта...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = if (query.isNotEmpty()) {
                    { IconButton(onClick = { viewModel.setQuery("") }) { Icon(Icons.Default.Clear, null) } }
                } else null,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                singleLine = true
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SortHeader("Название", "name", sortField, sortAsc, Modifier.weight(1f)) { viewModel.setSort("name") }
                SortHeader("УВ", "carbs", sortField, sortAsc) { viewModel.setSort("carbs") }
                SortHeader("ГИ", "gi", sortField, sortAsc, Modifier.padding(start = 12.dp)) { viewModel.setSort("gi") }
                SortHeader("Ккал", "calories", sortField, sortAsc, Modifier.padding(start = 12.dp)) { viewModel.setSort("calories") }
            }
            HorizontalDivider()

            if (products.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(120.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.NoFood, null,
                                    modifier = Modifier.size(56.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                        Text("Список продуктов пуст", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("Добавьте продукт или импортируйте CSV", color = MaterialTheme.colorScheme.outline)
                    }
                }
            } else {
                LazyColumn {
                    items(products, key = { it.id }) { product ->
                        ListItem(
                            headlineContent = { Text(product.name) },
                            supportingContent = {
                                Text("УВ: %.1f  Б: %.1f  Ж: %.1f  ГИ: %.0f".format(
                                    product.carbs, product.proteins, product.fats, product.glycemicIndex),
                                    fontSize = 12.sp)
                            },
                            trailingContent = {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("%.0f".format(product.calories), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    Text("ккал", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                                }
                            },
                            modifier = Modifier.clickable { editProduct = product }
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
        }
    }

    if (showAddDialog || editProduct != null) {
        ProductEditDialog(
            product = editProduct,
            onDismiss = { showAddDialog = false; editProduct = null },
            onSave = { viewModel.save(it); showAddDialog = false; editProduct = null },
            onDelete = editProduct?.let { p -> { viewModel.delete(p.id); showAddDialog = false; editProduct = null } }
        )
    }

    if (showImportDialog && importUri != null) {
        ImportDialog(
            onDismiss = { showImportDialog = false },
            onConfirm = { mode -> viewModel.importCsv(context, importUri!!, mode); showImportDialog = false }
        )
    }
}

@Composable
private fun SortHeader(
    label: String, field: String, current: String, asc: Boolean,
    modifier: Modifier = Modifier, onClick: () -> Unit
) {
    Row(modifier.clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontWeight = if (current == field) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp)
        if (current == field) {
            Icon(if (asc) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward, null, Modifier.size(14.dp))
        }
    }
}

@Composable
fun ProductEditDialog(
    product: Product?,
    onDismiss: () -> Unit,
    onSave: (Product) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var name     by remember { mutableStateOf(product?.name ?: "") }
    var calories by remember { mutableStateOf(product?.calories?.toString() ?: "0") }
    var proteins by remember { mutableStateOf(product?.proteins?.toString() ?: "0") }
    var fats     by remember { mutableStateOf(product?.fats?.toString() ?: "0") }
    var carbs    by remember { mutableStateOf(product?.carbs?.toString() ?: "0") }
    var gi       by remember { mutableStateOf(product?.glycemicIndex?.toString() ?: "50") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (product == null) "Новый продукт" else "Редактировать") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("Название") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumField("Калории", calories, Modifier.weight(1f)) { calories = it }
                    NumField("ГИ", gi, Modifier.weight(1f)) { gi = it }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumField("Углеводы", carbs, Modifier.weight(1f)) { carbs = it }
                    NumField("Белки", proteins, Modifier.weight(1f)) { proteins = it }
                }
                NumField("Жиры", fats, Modifier.fillMaxWidth()) { fats = it }
            }
        },
        confirmButton = {
            Row {
                onDelete?.let { del ->
                    TextButton(onClick = del) {
                        Text("Удалить", color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier)
                }
                TextButton(onClick = {
                    if (name.isBlank()) return@TextButton
                    onSave(Product(
                        id = product?.id ?: 0, name = name.trim(),
                        calories = calories.toDoubleOrNull() ?: 0.0,
                        proteins = proteins.toDoubleOrNull() ?: 0.0,
                        fats = fats.toDoubleOrNull() ?: 0.0,
                        carbs = carbs.toDoubleOrNull() ?: 0.0,
                        glycemicIndex = gi.toDoubleOrNull() ?: 50.0
                    ))
                }) { Text("Сохранить") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun NumField(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) },
        modifier = modifier, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
}

@Composable
private fun ImportDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var mode by remember { mutableStateOf("skip") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Режим импорта") },
        text = {
            Column {
                Text("Что делать с дубликатами?", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                listOf("skip" to "Пропустить", "update" to "Обновить", "add" to "Добавить с суффиксом").forEach { (key, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { mode = key }) {
                        RadioButton(selected = mode == key, onClick = { mode = key })
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(mode) }) { Text("Импорт") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
