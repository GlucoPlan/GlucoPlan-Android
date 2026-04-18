package com.glucoplan.app.ui.dishes

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SoupKitchen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.glucoplan.app.domain.model.Dish
import com.glucoplan.app.domain.model.DishIngredient
import com.glucoplan.app.domain.model.DishWithIngredients
import com.glucoplan.app.domain.model.Pan
import com.glucoplan.app.ui.products.ProductsViewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DishesScreen(
    viewModel: DishesViewModel = hiltViewModel(),
    onNavigateToPans: () -> Unit = {},
    onBack: (() -> Unit)? = null
) {
    val dishes by viewModel.dishes.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var editDish by remember { mutableStateOf<DishWithIngredients?>(null) }
    var showAddDish by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(query) { viewModel.load(query) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Блюда") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, "Назад")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToPans) {
                        Icon(Icons.Default.Kitchen, "Кастрюли")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDish = true }) {
                Icon(Icons.Default.Add, "Добавить")
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Поиск блюда...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = if (query.isNotEmpty()) {
                    { IconButton(onClick = { query = "" }) { Icon(Icons.Default.Clear, null) } }
                } else null,
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                singleLine = true
            )
            if (dishes.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.SoupKitchen, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.height(16.dp))
                        Text("Создайте первое блюдо", color = MaterialTheme.colorScheme.outline)
                    }
                }
            } else {
                LazyColumn {
                    items(dishes, key = { it.dish.id }) { dish ->
                        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                            ListItem(
                                headlineContent = { Text(dish.dish.name, fontWeight = FontWeight.Bold) },
                                supportingContent = {
                                    Text("УВ: %.1f г/100г  ·  Ингредиентов: ${dish.ingredients.size}".format(dish.carbsPer100g))
                                },
                                trailingContent = {
                                    Row {
                                        IconButton(onClick = { editDish = dish }) {
                                            Icon(Icons.Default.Edit, "Редактировать")
                                        }
                                        IconButton(onClick = {
                                            scope.launch { viewModel.deleteDish(dish.dish.id) }
                                        }) {
                                            Icon(Icons.Default.Delete, "Удалить")
                                        }
                                    }
                                },
                                modifier = Modifier.clickable { editDish = dish }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDish || editDish != null) {
        DishEditScreen(
            dish = editDish,
            viewModel = viewModel,
            onDismiss = { showAddDish = false; editDish = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DishEditScreen(
    dish: DishWithIngredients?,
    viewModel: DishesViewModel,
    productsViewModel: ProductsViewModel = hiltViewModel(),
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(dish?.dish?.name ?: "") }
    var grossWeight by remember { mutableStateOf(dish?.dish?.defaultCookedWeight?.toString() ?: "0") }
    var selectedPanId by remember { mutableStateOf(dish?.dish?.defaultPanId) }
    var ingredients by remember { mutableStateOf(dish?.ingredients?.toMutableList() ?: mutableListOf<DishIngredient>()) }
    val pans by viewModel.pans.collectAsStateWithLifecycle()
    var showAddIngredient by remember { mutableStateOf(false) }

    val selectedPan = pans.firstOrNull { it.id == selectedPanId }
    val netWeight = (grossWeight.toDoubleOrNull() ?: 0.0) - (selectedPan?.weight ?: 0.0)
    val totalCarbs = ingredients.sumOf { it.carbsInPortion }
    val totalWeight = ingredients.sumOf { it.weight }
    val carbsPer100g = if (totalWeight > 0) totalCarbs / totalWeight * 100.0 else 0.0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (dish == null) "Новое блюдо" else "Редактировать блюдо") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.ArrowBack, null) }
                },
                actions = {
                    IconButton(onClick = {
                        if (name.isBlank()) return@IconButton
                        viewModel.saveDish(
                            Dish(
                                id = dish?.dish?.id ?: 0, name = name.trim(),
                                defaultPanId = selectedPanId,
                                defaultCookedWeight = grossWeight.toDoubleOrNull() ?: 0.0
                            ),
                            ingredients
                        )
                        onDismiss()
                    }) { Icon(Icons.Default.Save, "Сохранить") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("Название блюда") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(12.dp))

                PanDropdown(pans = pans, selectedPanId = selectedPanId, onSelect = { selectedPanId = it })

                selectedPan?.photoPath?.let { path ->
                    if (File(path).exists()) {
                        Spacer(Modifier.height(8.dp))
                        AsyncImage(
                            model = File(path), contentDescription = selectedPan.name,
                            modifier = Modifier.fillMaxWidth().height(100.dp).clip(MaterialTheme.shapes.medium)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = grossWeight, onValueChange = { grossWeight = it },
                    label = { Text("Вес брутто (г)") }, suffix = { Text("г") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                if (selectedPan != null && netWeight > 0) {
                    Text("Нетто: %.0f г (брутто − %.0f г кастрюли)".format(netWeight, selectedPan.weight),
                        color = MaterialTheme.colorScheme.primary, fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(Modifier.height(12.dp))
                Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), Arrangement.SpaceAround) {
                        DishStat("УВ всего", "%.1f г".format(totalCarbs))
                        DishStat("Вес состава", "%.0f г".format(totalWeight))
                        DishStat("УВ/100г", "%.1f г".format(carbsPer100g))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("Состав", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { showAddIngredient = true }) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                        Text("Добавить")
                    }
                }
            }

            items(ingredients.size) { idx ->
                val ing = ingredients[idx]
                IngredientRow(
                    ingredient = ing,
                    onWeightChange = { w ->
                        ingredients = ingredients.toMutableList().also { it[idx] = ing.copy(weight = w) }
                    },
                    onRemove = {
                        ingredients = ingredients.toMutableList().also { it.removeAt(idx) }
                    }
                )
                HorizontalDivider()
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showAddIngredient) {
        AddIngredientDialog(
            productsViewModel = productsViewModel,
            onDismiss = { },
            onAdd = { ing ->
                ingredients = (ingredients + ing).toMutableList()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PanDropdown(pans: List<Pan>, selectedPanId: Long?, onSelect: (Long?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = pans.firstOrNull { it.id == selectedPanId }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.let { "${it.name} (${it.weight.toInt()} г)" } ?: "Без кастрюли",
            onValueChange = {},
            readOnly = true,
            label = { Text("Кастрюля / Посуда") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Без кастрюли") }, onClick = { onSelect(null); expanded = false })
            pans.forEach { pan ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (pan.photoPath != null && File(pan.photoPath).exists()) {
                                AsyncImage(
                                    model = File(pan.photoPath), contentDescription = null,
                                    modifier = Modifier.size(32.dp).clip(MaterialTheme.shapes.extraSmall)
                                )
                            } else {
                                Icon(Icons.Default.Kitchen, null, Modifier.size(32.dp))
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("${pan.name} (${pan.weight.toInt()} г)")
                        }
                    },
                    onClick = { onSelect(pan.id); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun IngredientRow(
    ingredient: DishIngredient,
    onWeightChange: (Double) -> Unit,
    onRemove: () -> Unit
) {
    var wText by remember(ingredient.id, ingredient.weight) {
        mutableStateOf("%.0f".format(ingredient.weight))
    }
    ListItem(
        headlineContent = { Text(ingredient.productName) },
        supportingContent = { Text("УВ: %.1f г".format(ingredient.carbsInPortion)) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = wText,
                    onValueChange = { v ->
                        wText = v
                        v.toDoubleOrNull()?.let { w -> if (w > 0) onWeightChange(w) }
                    },
                    modifier = Modifier.width(80.dp),
                    suffix = { Text("г") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.RemoveCircleOutline, null, Modifier.size(20.dp))
                }
            }
        }
    )
}


@Composable
private fun DishStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
        Text(value, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AddIngredientDialog(
    productsViewModel: ProductsViewModel,
    onDismiss: () -> Unit,
    onAdd: (DishIngredient) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var weightText by remember { mutableStateOf("100") }
    var selected by remember { mutableStateOf<com.glucoplan.app.domain.model.Product?>(null) }
    val results by productsViewModel.searchResults.collectAsStateWithLifecycle()

    LaunchedEffect(query) { productsViewModel.search(query) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить ингредиент") },
        text = {
            Column {
                OutlinedTextField(value = query, onValueChange = { query = it },
                    label = { Text("Поиск...") }, leadingIcon = { Icon(Icons.Default.Search, null) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                if (selected != null) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = weightText, onValueChange = { weightText = it },
                        label = { Text("Вес (г)") }, suffix = { Text("г") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.size(width = 280.dp, height = 250.dp)) {
                    items(results) { p ->
                        ListItem(
                            headlineContent = { Text(p.name) },
                            supportingContent = { Text("УВ: %.1f г/100г".format(p.carbs)) },
                            modifier = Modifier.clickable { selected = p }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val w = weightText.replace(',', '.').toDoubleOrNull() ?: return@TextButton
                    val p = selected ?: return@TextButton
                    onAdd(DishIngredient(productId = p.id, productName = p.name, weight = w,
                        carbs = p.carbs, calories = p.calories, proteins = p.proteins,
                        fats = p.fats, glycemicIndex = p.glycemicIndex))
                },
                enabled = selected != null
            ) { Text("Добавить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

private fun DishIngredient.copy(weight: Double) = DishIngredient(
    id = id, productId = productId, productName = productName, weight = weight,
    carbs = carbs, calories = calories, proteins = proteins, fats = fats, glycemicIndex = glycemicIndex
)
