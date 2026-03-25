package com.glucoplan.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glucoplan.app.core.NightscoutClient
import com.glucoplan.app.core.NsResult
import com.glucoplan.app.data.repository.GlucoRepository
import com.glucoplan.app.domain.model.AppSettings
import com.glucoplan.app.domain.model.InsulinProfiles
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import timber.log.Timber
import javax.inject.Inject

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val loading: Boolean = true,
    val nsCheckResult: NsResult<Unit>? = null,
    val nsChecking: Boolean = false,
    val insulinOptions: List<Pair<String, String>> = emptyList(),
    val basalOptions: List<Pair<String, String>> = emptyList()
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: GlucoRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val settings = repo.getSettings()
            Timber.d("Settings loaded: insulinType=${settings.insulinType}, basalType=${settings.basalType}")
            _state.update {
                it.copy(
                    settings = settings,
                    loading = false,
                    insulinOptions = InsulinProfiles.getBolusDisplayNames(),
                    basalOptions = listOf("none" to "Нет") + InsulinProfiles.getBasalDisplayNames()
                )
            }
        }
    }

    fun save(settings: AppSettings) {
        viewModelScope.launch {
            Timber.d("Saving settings")
            repo.saveSettings(settings)
            _state.update { it.copy(settings = settings) }
        }
    }

    fun checkNightscout(url: String, secret: String) {
        viewModelScope.launch {
            Timber.d("Checking Nightscout connection: url=$url")
            _state.update { it.copy(nsChecking = true, nsCheckResult = null) }

            val client = NightscoutClient(url, secret)
            val result = withContext(Dispatchers.IO) {
                client.checkConnection()
            }

            when (result) {
                is NsResult.Success -> {
                    Timber.i("Nightscout connection successful")
                }
                is NsResult.Error -> {
                    Timber.w("Nightscout connection failed: ${result.message}")
                }
            }

            _state.update { it.copy(nsChecking = false, nsCheckResult = result) }
        }
    }

    fun clearNsCheckResult() {
        _state.update { it.copy(nsCheckResult = null) }
    }
}
