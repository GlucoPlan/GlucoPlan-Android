package com.glucoplan.app.ui.products

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.FileProvider
import com.glucoplan.app.data.repository.GlucoRepository
import com.glucoplan.app.domain.model.Product
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ProductsViewModel @Inject constructor(
    private val repo: GlucoRepository
) : ViewModel() {

    val products = repo.getProductsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchResults = MutableStateFlow<List<Product>>(emptyList())
    val searchResults: StateFlow<List<Product>> = _searchResults.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _sortField = MutableStateFlow("name")
    val sortField: StateFlow<String> = _sortField.asStateFlow()

    private val _sortAsc = MutableStateFlow(true)
    val sortAsc: StateFlow<Boolean> = _sortAsc.asStateFlow()

    val filteredProducts = combine(products, _query, _sortField, _sortAsc) { list, q, field, asc ->
        val filtered = if (q.isBlank()) list
        else list.filter { it.name.contains(q, ignoreCase = true) }
        when (field) {
            "carbs"    -> if (asc) filtered.sortedBy { it.carbs }    else filtered.sortedByDescending { it.carbs }
            "calories" -> if (asc) filtered.sortedBy { it.calories } else filtered.sortedByDescending { it.calories }
            "gi"       -> if (asc) filtered.sortedBy { it.glycemicIndex } else filtered.sortedByDescending { it.glycemicIndex }
            else       -> if (asc) filtered.sortedBy { it.name.lowercase() } else filtered.sortedByDescending { it.name.lowercase() }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setQuery(q: String) { _query.value = q }

    fun setSort(field: String) {
        if (_sortField.value == field) _sortAsc.value = !_sortAsc.value
        else { _sortField.value = field; _sortAsc.value = true }
    }

    fun search(q: String) {
        viewModelScope.launch {
            _searchResults.value = repo.searchProducts(q)
        }
    }

    fun save(product: Product) {
        viewModelScope.launch { repo.saveProduct(product) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repo.deleteProduct(id) }
    }

    fun importCsv(context: Context, uri: Uri, mode: String) {
        viewModelScope.launch {
            try {
                val lines = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.readLines() ?: return@launch
                for (line in lines) {
                    val cols = line.split(";").map { it.trim().trimStart('\uFEFF') }
                    if (cols.size < 6) continue
                    val name = cols[0]
                    if (name.equals("Название", ignoreCase = true) || name.isBlank()) continue
                    val cal   = cols[1].replace(',', '.').toDoubleOrNull() ?: continue
                    val prot  = cols[2].replace(',', '.').toDoubleOrNull() ?: 0.0
                    val fat   = cols[3].replace(',', '.').toDoubleOrNull() ?: 0.0
                    val carbs = cols[4].replace(',', '.').toDoubleOrNull() ?: 0.0
                    val gi    = cols[5].replace(',', '.').toDoubleOrNull() ?: 50.0
                    val existing = repo.searchProducts(name).firstOrNull { it.name == name }
                    when {
                        existing == null  -> repo.saveProduct(Product(name = name, calories = cal, proteins = prot, fats = fat, carbs = carbs, glycemicIndex = gi))
                        mode == "update"  -> repo.saveProduct(existing.copy(calories = cal, proteins = prot, fats = fat, carbs = carbs, glycemicIndex = gi))
                        mode == "add"     -> repo.saveProduct(Product(name = "$name (импорт)", calories = cal, proteins = prot, fats = fat, carbs = carbs, glycemicIndex = gi))
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun exportCsv(context: Context) {
        viewModelScope.launch {
            try {
                val all = repo.searchProducts("")
                val sb = StringBuilder("\uFEFFНазвание;Калории;Белки;Жиры;Углеводы;ГИ\n")
                all.forEach { p -> sb.append("${p.name};${p.calories};${p.proteins};${p.fats};${p.carbs};${p.glycemicIndex}\n") }
                val file = File(context.cacheDir, "glucoplan_products.csv")
                file.writeText(sb.toString(), Charsets.UTF_8)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Экспорт продуктов"))
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
}
