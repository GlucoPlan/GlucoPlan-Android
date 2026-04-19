package com.glucoplan.app.ui.calculator

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.glucoplan.app.domain.model.CalcComponent
import com.glucoplan.app.domain.model.ComponentType
import com.glucoplan.app.domain.model.DishWithIngredients
import com.glucoplan.app.domain.model.Product
import com.glucoplan.app.ui.dishes.DishesViewModel
import com.glucoplan.app.ui.products.ProductsViewModel

// ─── ComponentCard ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComponentCard(
    component: CalcComponent,
    onWeightChanged: (Double) -> Unit,
    onRemove: () -> Unit,
    onToggleAdjust: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var weightText by remember(component.key) {
        mutableStateOf("%.1f".format(component.servingWeight))
    }
    var isFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(component.servingWeight) {
        if (!isFocused) {
            weightText = "%.1f".format(component.servingWeight)
        }
    }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) showDeleteDialog = true
            false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.error)
                    .padding(end = 16.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.onError)
            }
        },
        enableDismissFromStartToEnd = false
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            colors = if (component.type == ComponentType.DISH)
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f))
            else CardDefaults.cardColors()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = component.includedInAdjustment,
                        onCheckedChange = { onToggleAdjust() },
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = if (component.type == ComponentType.DISH) Icons.Default.Restaurant
                                      else Icons.Default.Grain,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (component.type == ComponentType.DISH)
                            MaterialTheme.colorScheme.secondary
                        else
                            MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        component.name,
                        modifier = Modifier.weight(1f),
                        fontWeight = if (component.type == ComponentType.DISH) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 15.sp
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = weightText,
                        onValueChange = { weightText = it },
                        modifier = Modifier
                            .width(90.dp)
                            .onFocusChanged { fs ->
                                isFocused = fs.isFocused
                                if (!fs.isFocused) {
                                    val v = weightText.replace(',', '.').toDoubleOrNull()
                                    if (v != null && v > 0) onWeightChanged(v)
                                    else weightText = "%.1f".format(component.servingWeight)
                                }
                            },
                        suffix = { Text("г") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            val v = weightText.replace(',', '.').toDoubleOrNull()
                            if (v != null && v > 0) onWeightChanged(v)
                            else weightText = "%.1f".format(component.servingWeight)
                        }),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(modifier = Modifier.padding(start = 36.dp)) {
                    InfoChip("УВ: %.1f г".format(component.carbsInPortion), MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    InfoChip("ГН: %.1f".format(component.glycemicLoad), MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(8.dp))
                    InfoChip("%.0f ккал".format(component.caloriesInPortion))
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Удалить?") },
            text = { Text("Удалить «${component.name}» из списка?") },
            confirmButton = {
                TextButton(onClick = { onRemove(); showDeleteDialog = false }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Отмена") }
            }
        )
    }
}

// ─── InfoChip ─────────────────────────────────────────────────────────────────

@Composable
fun InfoChip(
    label: String,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.outline
) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(50)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            fontSize = 11.sp,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ─── AddComponentDialog ───────────────────────────────────────────────────────

@Composable
fun AddComponentDialog(
    type: ComponentDialogType,
    onDismiss: () -> Unit,
    onAddProduct: (Product, Double) -> Unit,
    onAddDish: (DishWithIngredients, Double) -> Unit,
    productsViewModel: ProductsViewModel = hiltViewModel(),
    dishesViewModel: DishesViewModel = hiltViewModel()
) {
    var query by remember { mutableStateOf("") }
    var weightText by remember { mutableStateOf("100") }
    var selectedProduct by remember { mutableStateOf<Product?>(null) }
    var selectedDish by remember { mutableStateOf<DishWithIngredients?>(null) }
    val hasSelection = selectedProduct != null || selectedDish != null

    val productResults by productsViewModel.searchResults.collectAsState()
    val dishResults by dishesViewModel.searchResults.collectAsState()

    LaunchedEffect(query) {
        if (type == ComponentDialogType.PRODUCT) productsViewModel.search(query)
        else dishesViewModel.searchWithIngredients(query)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (type == ComponentDialogType.PRODUCT) "Добавить продукт" else "Добавить блюдо") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Поиск...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = if (query.isNotEmpty()) {
                        { IconButton(onClick = { query = "" }) { Icon(Icons.Default.Clear, null) } }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (hasSelection) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = weightText,
                        onValueChange = { weightText = it },
                        label = { Text("Вес порции (г)") },
                        suffix = { Text("г") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    if (type == ComponentDialogType.PRODUCT) {
                        if (productResults.isEmpty()) {
                            item { Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) { Text("Ничего не найдено") } }
                        } else {
                            items(productResults) { product ->
                                ListItem(
                                    headlineContent = { Text(product.name) },
                                    supportingContent = {
                                        Text("УВ: %.1f г  |  ГИ: %.0f".format(product.carbs, product.glycemicIndex))
                                    },
                                    modifier = Modifier
                                        .clickable { selectedProduct = product }
                                        .background(
                                            if (selectedProduct?.id == product.id)
                                                MaterialTheme.colorScheme.primaryContainer
                                            else androidx.compose.ui.graphics.Color.Transparent
                                        )
                                )
                            }
                        }
                    } else {
                        if (dishResults.isEmpty()) {
                            item { Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) { Text("Ничего не найдено") } }
                        } else {
                            items(dishResults) { dish ->
                                ListItem(
                                    headlineContent = { Text(dish.dish.name) },
                                    supportingContent = {
                                        Text("УВ: %.1f г/100г".format(dish.carbsPer100g))
                                    },
                                    modifier = Modifier
                                        .clickable { selectedDish = dish }
                                        .background(
                                            if (selectedDish?.dish?.id == dish.dish.id)
                                                MaterialTheme.colorScheme.primaryContainer
                                            else androidx.compose.ui.graphics.Color.Transparent
                                        )
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val w = weightText.replace(',', '.').toDoubleOrNull() ?: return@TextButton
                    if (w <= 0) return@TextButton
                    if (type == ComponentDialogType.PRODUCT) {
                        selectedProduct?.let { onAddProduct(it, w) }
                    } else {
                        selectedDish?.let { onAddDish(it, w) }
                    }
                },
                enabled = hasSelection
            ) { Text("Добавить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
