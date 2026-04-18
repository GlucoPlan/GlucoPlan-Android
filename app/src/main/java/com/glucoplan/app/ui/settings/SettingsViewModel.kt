package com.glucoplan.app.ui.settings

import androidx.lifecycle.ViewModel
import com.glucoplan.app.BuildConfig as AppBuildConfig
import kotlinx.coroutines.Dispatchers
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
    val basalOptions: List<Pair<String, String>> = emptyList(),
    // Обновление
    val updateAvailable: Boolean = false,
    val latestVersion: String? = null,
    val latestApkUrl: String? = null,
    val updateChecking: Boolean = false
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

    // ─── Проверка обновлений ──────────────────────────────────────────────────

    fun checkForUpdates() {
        viewModelScope.launch {
            _state.update { it.copy(updateChecking = true) }
            try {
                val client = okhttp3.OkHttpClient()
                val request = okhttp3.Request.Builder()
                    .url("https://api.github.com/repos/GlucoPlan/GlucoPlan-Android/releases/latest")
                    .header("Accept", "application/vnd.github.v3+json")
                    .build()
                val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
                val body = response.body?.string() ?: run {
                    _state.update { it.copy(updateChecking = false) }
                    return@launch
                }
                val json = org.json.JSONObject(body)
                val tagName = json.optString("tag_name", "")   // "v0.3.5"
                val latestVer = tagName.removePrefix("v")
                val currentVer = AppBuildConfig.VERSION_NAME

                // Ищем URL APK в assets релиза
                val assets = json.optJSONArray("assets")
                var apkUrl: String? = null
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        if (asset.optString("name", "").endsWith(".apk")) {
                            apkUrl = asset.optString("browser_download_url")
                            break
                        }
                    }
                }

                val isNewer = isVersionNewer(latestVer, currentVer)
                Timber.i("Update check: current=$currentVer latest=$latestVer newer=$isNewer")
                _state.update { it.copy(
                    updateChecking = false,
                    updateAvailable = isNewer,
                    latestVersion  = if (isNewer) latestVer else null,
                    latestApkUrl   = if (isNewer) apkUrl else null
                )}
            } catch (e: Exception) {
                Timber.w(e, "Update check failed")
                _state.update { it.copy(updateChecking = false) }
            }
        }
    }

    fun clearUpdateState() {
        _state.update { it.copy(updateAvailable = false, latestVersion = null, latestApkUrl = null) }
    }

    private fun isVersionNewer(latest: String, current: String): Boolean {
        return try {
            val l = latest.trim().split(".").map { it.toInt() }
            val c = current.trim().split(".").map { it.toInt() }
            for (i in 0 until maxOf(l.size, c.size)) {
                val lv = l.getOrElse(i) { 0 }
                val cv = c.getOrElse(i) { 0 }
                if (lv > cv) return true
                if (lv < cv) return false
            }
            false
        } catch (e: Exception) { false }
    }
}
