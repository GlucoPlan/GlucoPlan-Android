package com.glucoplan.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glucoplan.app.core.NightscoutClient
import com.glucoplan.app.core.NsResult
import com.glucoplan.app.data.repository.GlucoRepository
import com.glucoplan.app.domain.model.AppSettings
import com.glucoplan.app.domain.model.InsulinProfiles
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val loading: Boolean = true,
    val nsCheckResult: NsResult<Unit>? = null,
    val nsChecking: Boolean = false,
    val nsSyncing: Boolean = false,
    val nsSyncResult: NsSyncResult? = null,
    val insulinOptions: List<Pair<String, String>> = emptyList(),
    val basalOptions: List<Pair<String, String>> = emptyList()
)

sealed class NsSyncResult {
    object UploadSuccess : NsSyncResult()
    object DownloadSuccess : NsSyncResult()
    data class Error(val message: String) : NsSyncResult()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: GlucoRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            val settings = repo.getSettings()
            Timber.d("Settings loaded: insulinType=${settings.insulinType}")
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
            repo.saveSettings(settings)
            _state.update { it.copy(settings = settings) }
        }
    }

    fun checkNightscout(url: String, secret: String) {
        viewModelScope.launch {
            _state.update { it.copy(nsChecking = true, nsCheckResult = null) }
            val client = NightscoutClient(url, secret)
            val result = withContext(Dispatchers.IO) { client.checkConnection() }
            when (result) {
                is NsResult.Success -> Timber.i("NS connection OK")
                is NsResult.Error   -> Timber.w("NS connection failed: ${result.message}")
            }
            _state.update { it.copy(nsChecking = false, nsCheckResult = result) }
        }
    }

    /** Загрузить настройки из Nightscout → применить локально */
    fun loadFromNightscout() {
        val s = _state.value.settings
        if (!s.nsEnabled || s.nsUrl.isBlank()) return

        viewModelScope.launch {
            _state.update { it.copy(nsSyncing = true, nsSyncResult = null) }

            val client = NightscoutClient(s.nsUrl, s.nsApiSecret)
            val result = withContext(Dispatchers.IO) {
                client.loadSettingsFromProfile(s)
            }

            when (result) {
                is NsResult.Success -> {
                    Timber.i("Settings loaded from NS")
                    repo.saveSettings(result.data)
                    _state.update {
                        it.copy(
                            settings = result.data,
                            nsSyncing = false,
                            nsSyncResult = NsSyncResult.DownloadSuccess
                        )
                    }
                }
                is NsResult.Error -> {
                    Timber.w("Failed to load from NS: ${result.message}")
                    _state.update {
                        it.copy(
                            nsSyncing = false,
                            nsSyncResult = NsSyncResult.Error(result.message)
                        )
                    }
                }
            }
        }
    }

    /** Сохранить текущие настройки → Nightscout */
    fun uploadToNightscout() {
        val s = _state.value.settings
        if (!s.nsEnabled || s.nsUrl.isBlank()) return

        viewModelScope.launch {
            _state.update { it.copy(nsSyncing = true, nsSyncResult = null) }

            val client = NightscoutClient(s.nsUrl, s.nsApiSecret)
            val result = withContext(Dispatchers.IO) {
                client.saveSettingsToProfile(s)
            }

            when (result) {
                is NsResult.Success -> {
                    Timber.i("Settings uploaded to NS")
                    _state.update {
                        it.copy(nsSyncing = false, nsSyncResult = NsSyncResult.UploadSuccess)
                    }
                }
                is NsResult.Error -> {
                    Timber.w("Failed to upload to NS: ${result.message}")
                    _state.update {
                        it.copy(
                            nsSyncing = false,
                            nsSyncResult = NsSyncResult.Error(result.message)
                        )
                    }
                }
            }
        }
    }

    fun clearNsCheckResult()  { _state.update { it.copy(nsCheckResult = null) } }
    fun clearNsSyncResult()   { _state.update { it.copy(nsSyncResult = null) } }
}
