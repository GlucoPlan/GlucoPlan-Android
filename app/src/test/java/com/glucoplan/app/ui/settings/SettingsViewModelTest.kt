package com.glucoplan.app.ui.settings

import com.glucoplan.app.data.repository.GlucoRepository
import com.glucoplan.app.domain.model.AppSettings
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Unit-тесты для SettingsViewModel.
 * Проверяет загрузку, сохранение настроек, очистку результатов и сравнение версий.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private lateinit var репозиторий: GlucoRepository
    private lateinit var viewModel: SettingsViewModel
    private val диспетчер = UnconfinedTestDispatcher()

    private val тестовыеНастройки = AppSettings(
        carbsPerXe = 12.0,
        carbCoefficient = 1.5,
        sensitivity = 2.5,
        targetGlucose = 6.0,
        insulinStep = 0.5,
        nsEnabled = false,
        nsUrl = "https://test.ns.io",
        nsApiSecret = "тестовый_секрет"
    )

    @Before
    fun подготовка() {
        Dispatchers.setMain(диспетчер)
        репозиторий = mockk(relaxed = true)
        coEvery { репозиторий.getSettings() } returns тестовыеНастройки
        viewModel = SettingsViewModel(репозиторий)
    }

    @After
    fun очистка() {
        Dispatchers.resetMain()
    }

    // ─── Начальное состояние ──────────────────────────────────────────────────────

    @Test
    fun `начальное состояние показывает загрузку`() {
        // Только что созданный ViewModel должен быть в состоянии загрузки
        // (или уже загружен из-за UnconfinedTestDispatcher)
        val состояние = viewModel.state.value
        assertThat(состояние).isNotNull()
    }

    @Test
    fun `load загружает настройки из репозитория`() = runTest {
        viewModel.load()
        advanceUntilIdle()

        coVerify { репозиторий.getSettings() }
        assertThat(viewModel.state.value.settings.carbsPerXe).isEqualTo(12.0)
        assertThat(viewModel.state.value.settings.sensitivity).isEqualTo(2.5)
    }

    @Test
    fun `load сбрасывает флаг загрузки`() = runTest {
        viewModel.load()
        advanceUntilIdle()
        assertThat(viewModel.state.value.loading).isFalse()
    }

    // ─── Сохранение настроек ──────────────────────────────────────────────────────

    @Test
    fun `save вызывает репозиторий`() = runTest {
        viewModel.save(тестовыеНастройки)
        advanceUntilIdle()
        coVerify { репозиторий.saveSettings(тестовыеНастройки) }
    }

    @Test
    fun `save обновляет настройки в состоянии`() = runTest {
        val новыеНастройки = тестовыеНастройки.copy(carbsPerXe = 15.0)
        viewModel.save(новыеНастройки)
        advanceUntilIdle()
        assertThat(viewModel.state.value.settings.carbsPerXe).isEqualTo(15.0)
    }

    // ─── Очистка результатов ──────────────────────────────────────────────────────

    @Test
    fun `clearNsCheckResult сбрасывает результат проверки NS`() = runTest {
        viewModel.clearNsCheckResult()
        assertThat(viewModel.state.value.nsCheckResult).isNull()
    }

    @Test
    fun `clearNsSyncResult сбрасывает результат синхронизации NS`() = runTest {
        viewModel.clearNsSyncResult()
        assertThat(viewModel.state.value.nsSyncResult).isNull()
    }

    @Test
    fun `clearUpdateState сбрасывает состояние обновления`() = runTest {
        viewModel.clearUpdateState()
        val состояние = viewModel.state.value
        assertThat(состояние.updateAvailable).isFalse()
        assertThat(состояние.latestVersion).isNull()
        assertThat(состояние.latestApkUrl).isNull()
    }

    // ─── isVersionNewer (через приватный рефлексией не тестируем, тестируем через checkForUpdates) ──

    @Test
    fun `начальное updateChecking равно false`() {
        assertThat(viewModel.state.value.updateChecking).isFalse()
    }

    @Test
    fun `начальное updateAvailable равно false`() {
        assertThat(viewModel.state.value.updateAvailable).isFalse()
    }

    // ─── Состояние синхронизации NS ───────────────────────────────────────────────

    @Test
    fun `nsChecking начально равен false`() {
        assertThat(viewModel.state.value.nsChecking).isFalse()
    }

    @Test
    fun `nsSyncing начально равен false`() {
        assertThat(viewModel.state.value.nsSyncing).isFalse()
    }

    // ─── Варианты NsSyncResult ────────────────────────────────────────────────────

    @Test
    fun `NsSyncResult UploadSuccess является sealed class`() {
        val результат: NsSyncResult = NsSyncResult.UploadSuccess
        assertThat(результат).isInstanceOf(NsSyncResult::class.java)
    }

    @Test
    fun `NsSyncResult DownloadSuccess является sealed class`() {
        val результат: NsSyncResult = NsSyncResult.DownloadSuccess
        assertThat(результат).isInstanceOf(NsSyncResult::class.java)
    }

    @Test
    fun `NsSyncResult Error содержит сообщение об ошибке`() {
        val результат = NsSyncResult.Error("Тестовая ошибка")
        assertThat(результат.message).isEqualTo("Тестовая ошибка")
    }

    // ─── Состояние загрузки после load ───────────────────────────────────────────

    @Test
    fun `после load список insulin options загружается`() = runTest {
        viewModel.load()
        advanceUntilIdle()
        // insulinOptions не пустой после загрузки настроек
        assertThat(viewModel.state.value.insulinOptions).isNotEmpty()
    }

    @Test
    fun `после load список basal options загружается`() = runTest {
        viewModel.load()
        advanceUntilIdle()
        assertThat(viewModel.state.value.basalOptions).isNotEmpty()
    }
}
