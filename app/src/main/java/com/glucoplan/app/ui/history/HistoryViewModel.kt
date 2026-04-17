package com.glucoplan.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glucoplan.app.data.db.MealComponentRow
import com.glucoplan.app.data.repository.GlucoRepository
import com.glucoplan.app.domain.model.CalcComponent
import com.glucoplan.app.domain.model.ComponentType
import com.glucoplan.app.domain.model.Meal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class HistoryUiState(
    val meals: List<Meal> = emptyList(),
    val loading: Boolean = true,
    val filterDate: LocalDate? = null
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repo: GlucoRepository
) : ViewModel() {

    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    private val _components = MutableStateFlow<List<MealComponentRow>>(emptyList())
    val components: StateFlow<List<MealComponentRow>> = _components.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val meals = repo.getMealsByDate(_state.value.filterDate)
            _state.update { it.copy(meals = meals, loading = false) }
        }
    }

    fun setFilter(date: LocalDate?) {
        _state.update { it.copy(filterDate = date) }
        load()
    }

    fun delete(meal: Meal) {
        viewModelScope.launch {
            repo.deleteMeal(meal.id)
            load()
        }
    }

    fun loadComponents(mealId: Long) {
        viewModelScope.launch {
            _components.value = repo.getMealComponents(mealId)
        }
    }

    suspend fun buildCalcComponents(mealId: Long): List<CalcComponent> {
        val rows = repo.getMealComponents(mealId)
        return rows.mapNotNull { row ->
            when {
                row.componentType == "product" && row.productId != null -> {
                    val p = repo.getProduct(row.productId) ?: return@mapNotNull null
                    CalcComponent.fromProduct(p, row.servingWeight)
                }
                row.componentType == "dish" && row.dishId != null -> {
                    val d = repo.getDishWithIngredients(row.dishId) ?: return@mapNotNull null
                    CalcComponent.fromDish(d, row.servingWeight)
                }
                else -> null
            }
        }
    }
}
