package com.glucoplan.app.ui.dishes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glucoplan.app.data.repository.GlucoRepository
import com.glucoplan.app.domain.model.Dish
import com.glucoplan.app.domain.model.DishIngredient
import com.glucoplan.app.domain.model.DishWithIngredients
import com.glucoplan.app.domain.model.Pan
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DishesViewModel @Inject constructor(
    private val repo: GlucoRepository
) : ViewModel() {

    private val _dishes = MutableStateFlow<List<DishWithIngredients>>(emptyList())
    val dishes: StateFlow<List<DishWithIngredients>> = _dishes.asStateFlow()

    private val _pans = MutableStateFlow<List<Pan>>(emptyList())
    val pans: StateFlow<List<Pan>> = _pans.asStateFlow()

    // Used by AddComponentDialog in Calculator
    private val _searchResults = MutableStateFlow<List<DishWithIngredients>>(emptyList())
    val searchResults: StateFlow<List<DishWithIngredients>> = _searchResults.asStateFlow()

    init {
        load("")
        loadPans()
    }

    fun load(query: String) {
        viewModelScope.launch {
            val results = repo.searchDishesWithIngredients(query)
            _dishes.value = results
            _searchResults.value = results
        }
    }

    fun searchWithIngredients(q: String) {
        viewModelScope.launch {
            _searchResults.value = repo.searchDishesWithIngredients(q)
        }
    }

    fun loadPans() {
        viewModelScope.launch { _pans.value = repo.getPans() }
    }

    fun saveDish(dish: Dish, ingredients: List<DishIngredient>) {
        viewModelScope.launch {
            repo.saveDish(dish, ingredients)
            load("")
        }
    }

    fun deleteDish(id: Long) {
        viewModelScope.launch {
            repo.deleteDish(id)
            load("")
        }
    }

    suspend fun savePan(pan: Pan): Long = repo.savePan(pan)

    suspend fun deletePan(id: Long) {
        repo.deletePan(id)
        loadPans()
    }
}
