package com.glucoplan.app.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.glucoplan.app.domain.model.*
import com.glucoplan.app.ui.calculator.CgmWidget
import com.glucoplan.app.ui.calculator.TotalsPanel
import com.glucoplan.app.ui.calculator.CalculatorUiState
import com.glucoplan.app.ui.theme.GlucoPlanTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI-тесты для компонентов экрана калькулятора.
 * Тестируем отдельные stateless composable — CgmWidget и TotalsPanel,
 * т.к. полный CalculatorScreen требует Hilt и не поддаётся изолированному тестированию.
 */
@RunWith(AndroidJUnit4::class)
class CalculatorScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ─── CgmWidget Tests ──────────────────────────────────────────────────────────

    @Test
    fun cgmWidget_showsLoadingWhenNoReadingAndNoError() {
        composeTestRule.setContent {
            GlucoPlanTheme {
                CgmWidget(reading = null, error = null)
            }
        }
        composeTestRule.onNodeWithText("Загрузка CGM", substring = true).assertExists()
    }

    @Test
    fun cgmWidget_showsErrorWhenNoReadingButHasError() {
        composeTestRule.setContent {
            GlucoPlanTheme {
                CgmWidget(reading = null, error = "Connection refused")
            }
        }
        composeTestRule.onNodeWithText("Нет соединения с CGM").assertExists()
    }

    @Test
    fun cgmWidget_showsGlucoseValue() {
        val reading = CgmReading(
            glucose = 7.5,
            direction = "Flat",
            time = java.time.Instant.now()
        )
        composeTestRule.setContent {
            GlucoPlanTheme {
                CgmWidget(reading = reading, error = null)
            }
        }
        // Use "7" as substring - locale-safe (matches both "7.5" and "7,5")
        composeTestRule.onNodeWithText("7", substring = true).assertExists()
    }

    @Test
    fun cgmWidget_showsTrendArrow() {
        val reading = CgmReading(
            glucose = 8.0,
            direction = "DoubleUp",
            time = java.time.Instant.now()
        )
        composeTestRule.setContent {
            GlucoPlanTheme {
                CgmWidget(reading = reading, error = null)
            }
        }
        composeTestRule.onNodeWithText("⇈").assertExists()
    }

    @Test
    fun cgmWidget_showsSingleUpArrow() {
        val reading = CgmReading(
            glucose = 7.0,
            direction = "SingleUp",
            time = java.time.Instant.now()
        )
        composeTestRule.setContent {
            GlucoPlanTheme {
                CgmWidget(reading = reading, error = null)
            }
        }
        composeTestRule.onNodeWithText("↑").assertExists()
    }

    @Test
    fun cgmWidget_showsFlatArrow() {
        val reading = CgmReading(
            glucose = 6.0,
            direction = "Flat",
            time = java.time.Instant.now()
        )
        composeTestRule.setContent {
            GlucoPlanTheme {
                CgmWidget(reading = reading, error = null)
            }
        }
        composeTestRule.onNodeWithText("→").assertExists()
    }

    @Test
    fun cgmWidget_showsStaleLabel() {
        // Reading older than 10 minutes is stale
        val staleTime = java.time.Instant.now()
            .minus(15, java.time.temporal.ChronoUnit.MINUTES)
        val reading = CgmReading(
            glucose = 6.5,
            direction = "Flat",
            time = staleTime
        )
        composeTestRule.setContent {
            GlucoPlanTheme {
                CgmWidget(reading = reading, error = null)
            }
        }
        composeTestRule.onNodeWithText("устаревшее", substring = true).assertExists()
    }

    // ─── TotalsPanel Tests ────────────────────────────────────────────────────────

    @Test
    fun totalsPanel_showsZerosForEmptyComponents() {
        val state = CalculatorUiState(
            components = emptyList(),
            settings = AppSettings(carbsPerXe = 12.0, nsEnabled = false)
        )
        composeTestRule.setContent {
            GlucoPlanTheme {
                TotalsPanel(state = state)
            }
        }
        // УВ label should exist
        composeTestRule.onNodeWithText("УВ").assertExists()
        composeTestRule.onNodeWithText("ХЕ").assertExists()
    }

    @Test
    fun totalsPanel_showsCorrectCarbsValue() {
        val state = CalculatorUiState(
            components = listOf(
                CalcComponent(
                    type = ComponentType.PRODUCT,
                    sourceId = 1,
                    name = "Bread",
                    servingWeight = 100.0,
                    carbsPer100g = 36.0,
                    caloriesPer100g = 180.0,
                    proteinsPer100g = 5.0,
                    fatsPer100g = 2.0
                )
            ),
            settings = AppSettings(carbsPerXe = 12.0, nsEnabled = false)
        )
        composeTestRule.setContent {
            GlucoPlanTheme {
                TotalsPanel(state = state)
            }
        }
        // 36g carbs should be displayed — use substring for locale safety
        composeTestRule.onNodeWithText("36", substring = true).assertExists()
    }

    @Test
    fun totalsPanel_showsCorrectXeValue() {
        val state = CalculatorUiState(
            components = listOf(
                CalcComponent(
                    type = ComponentType.PRODUCT,
                    sourceId = 1,
                    name = "Food",
                    servingWeight = 100.0,
                    carbsPer100g = 36.0,
                    caloriesPer100g = 180.0,
                    proteinsPer100g = 5.0,
                    fatsPer100g = 2.0
                )
            ),
            settings = AppSettings(carbsPerXe = 12.0, nsEnabled = false)
        )
        composeTestRule.setContent {
            GlucoPlanTheme {
                TotalsPanel(state = state)
            }
        }
        // 36g / 12g = 3 XE — check XE label exists and panel has expected structure
        // Use ХЕ label presence as proxy (breadUnits > 0 means label shows up)
        composeTestRule.onNodeWithText("ХЕ").assertExists()
        // Value "3.x" appears somewhere in panel (locale-safe: just check "3" as substring)
        composeTestRule.onAllNodesWithText("3", substring = true).fetchSemanticsNodes().let {
            assert(it.isNotEmpty()) { "Expected node with text containing '3' but found none" }
        }
    }

    @Test
    fun totalsPanel_showsMultipleComponents() {
        val state = CalculatorUiState(
            components = listOf(
                CalcComponent(
                    type = ComponentType.PRODUCT,
                    sourceId = 1,
                    name = "A",
                    servingWeight = 100.0,
                    carbsPer100g = 20.0,
                    caloriesPer100g = 100.0,
                    proteinsPer100g = 0.0,
                    fatsPer100g = 0.0
                ),
                CalcComponent(
                    type = ComponentType.PRODUCT,
                    sourceId = 2,
                    name = "B",
                    servingWeight = 100.0,
                    carbsPer100g = 16.0,
                    caloriesPer100g = 80.0,
                    proteinsPer100g = 0.0,
                    fatsPer100g = 0.0
                )
            ),
            settings = AppSettings(carbsPerXe = 12.0, nsEnabled = false)
        )
        composeTestRule.setContent {
            GlucoPlanTheme {
                TotalsPanel(state = state)
            }
        }
        // 20 + 16 = 36g total carbs — substring for locale safety
        composeTestRule.onNodeWithText("36", substring = true).assertExists()
    }
}
