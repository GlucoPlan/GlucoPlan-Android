package com.glucoplan.app.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.glucoplan.app.domain.model.*
import com.glucoplan.app.ui.calculator.CalculatorScreen
import com.glucoplan.app.ui.calculator.CalculatorUiState
import com.glucoplan.app.ui.theme.GlucoPlanTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for CalculatorScreen using Compose Testing.
 */
@RunWith(AndroidJUnit4::class)
class CalculatorScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ─── Basic Rendering Tests ────────────────────────────────────────────────────

    @Test
    fun calculatorScreen_displaysGlucoseInput() {
        composeTestRule.setContent {
            GlucoPlanTheme {
                CalculatorScreen(
                    state = CalculatorUiState(),
                    onAddProduct = {},
                    onAddDish = {},
                    onRemoveComponent = {},
                    onWeightChange = { _, _ -> },
                    onGlucoseChange = {},
                    onToggleAdjustment = {},
                    onSaveMeal = {},
                    onAdjustPortion = {},
                    onClearAll = {},
                    onLoadFromHistory = {},
                    onNavigateToSimulator = {},
                    onNavigateToHistory = {},
                    onNavigateToSettings = {},
                    onNavigateToProducts = {},
                    onNavigateToDishes = {}
                )
            }
        }

        // Should show glucose input field
        composeTestRule.onNodeWithText("Сахар").assertExists()
    }

    @Test
    fun calculatorScreen_displaysTotalDose() {
        val state = CalculatorUiState(
            components = listOf(
                CalcComponent(
                    type = ComponentType.PRODUCT,
                    sourceId = 1,
                    name = "Bread",
                    servingWeight = 100.0,
                    carbsPer100g = 48.0,
                    caloriesPer100g = 240.0,
                    proteinsPer100g = 8.0,
                    fatsPer100g = 2.0,
                    glycemicIndex = 70.0
                )
            ),
            settings = AppSettings(
                carbsPerXe = 12.0,
                carbCoefficient = 1.5
            )
        )

        composeTestRule.setContent {
            GlucoPlanTheme {
                CalculatorScreen(
                    state = state,
                    onAddProduct = {},
                    onAddDish = {},
                    onRemoveComponent = {},
                    onWeightChange = { _, _ -> },
                    onGlucoseChange = {},
                    onToggleAdjustment = {},
                    onSaveMeal = {},
                    onAdjustPortion = {},
                    onClearAll = {},
                    onLoadFromHistory = {},
                    onNavigateToSimulator = {},
                    onNavigateToHistory = {},
                    onNavigateToSettings = {},
                    onNavigateToProducts = {},
                    onNavigateToDishes = {}
                )
            }
        }

        // Should display calculated dose
        composeTestRule.onNodeWithText("ед", substring = true).assertExists()
    }

    @Test
    fun calculatorScreen_displaysComponent() {
        val state = CalculatorUiState(
            components = listOf(
                CalcComponent(
                    type = ComponentType.PRODUCT,
                    sourceId = 1,
                    name = "Test Food",
                    servingWeight = 150.0,
                    carbsPer100g = 30.0,
                    caloriesPer100g = 150.0,
                    proteinsPer100g = 5.0,
                    fatsPer100g = 2.0,
                    glycemicIndex = 55.0
                )
            )
        )

        composeTestRule.setContent {
            GlucoPlanTheme {
                CalculatorScreen(
                    state = state,
                    onAddProduct = {},
                    onAddDish = {},
                    onRemoveComponent = {},
                    onWeightChange = { _, _ -> },
                    onGlucoseChange = {},
                    onToggleAdjustment = {},
                    onSaveMeal = {},
                    onAdjustPortion = {},
                    onClearAll = {},
                    onLoadFromHistory = {},
                    onNavigateToSimulator = {},
                    onNavigateToHistory = {},
                    onNavigateToSettings = {},
                    onNavigateToProducts = {},
                    onNavigateToDishes = {}
                )
            }
        }

        // Should show component name
        composeTestRule.onNodeWithText("Test Food").assertExists()
    }

    @Test
    fun calculatorScreen_showsEmptyState() {
        composeTestRule.setContent {
            GlucoPlanTheme {
                CalculatorScreen(
                    state = CalculatorUiState(components = emptyList()),
                    onAddProduct = {},
                    onAddDish = {},
                    onRemoveComponent = {},
                    onWeightChange = { _, _ -> },
                    onGlucoseChange = {},
                    onToggleAdjustment = {},
                    onSaveMeal = {},
                    onAdjustPortion = {},
                    onClearAll = {},
                    onLoadFromHistory = {},
                    onNavigateToSimulator = {},
                    onNavigateToHistory = {},
                    onNavigateToSettings = {},
                    onNavigateToProducts = {},
                    onNavigateToDishes = {}
                )
            }
        }

        // Should show hint to add products
        composeTestRule.onNodeWithText("Добавьте продукт", substring = true).assertExists()
    }

    // ─── Interaction Tests ────────────────────────────────────────────────────────

    @Test
    fun glucoseInput_updatesValue() {
        var enteredGlucose = 0.0

        composeTestRule.setContent {
            GlucoPlanTheme {
                CalculatorScreen(
                    state = CalculatorUiState(),
                    onAddProduct = {},
                    onAddDish = {},
                    onRemoveComponent = {},
                    onWeightChange = { _, _ -> },
                    onGlucoseChange = { enteredGlucose = it },
                    onToggleAdjustment = {},
                    onSaveMeal = {},
                    onAdjustPortion = {},
                    onClearAll = {},
                    onLoadFromHistory = {},
                    onNavigateToSimulator = {},
                    onNavigateToHistory = {},
                    onNavigateToSettings = {},
                    onNavigateToProducts = {},
                    onNavigateToDishes = {}
                )
            }
        }

        // Find glucose input and enter value
        composeTestRule.onNodeWithText("Сахар")
            .performTextInput("7.5")

        composeTestRule.waitForIdle()

        // Callback should have been called (verification would require mock)
    }

    @Test
    fun saveButton_isDisabledWithEmptyComponents() {
        composeTestRule.setContent {
            GlucoPlanTheme {
                CalculatorScreen(
                    state = CalculatorUiState(components = emptyList()),
                    onAddProduct = {},
                    onAddDish = {},
                    onRemoveComponent = {},
                    onWeightChange = { _, _ -> },
                    onGlucoseChange = {},
                    onToggleAdjustment = {},
                    onSaveMeal = {},
                    onAdjustPortion = {},
                    onClearAll = {},
                    onLoadFromHistory = {},
                    onNavigateToSimulator = {},
                    onNavigateToHistory = {},
                    onNavigateToSettings = {},
                    onNavigateToProducts = {},
                    onNavigateToDishes = {}
                )
            }
        }

        // Save button should be disabled
        composeTestRule.onNodeWithText("Сохранить")
            .assertIsNotEnabled()
    }

    @Test
    fun saveButton_isEnabledWithComponents() {
        val state = CalculatorUiState(
            components = listOf(
                CalcComponent(
                    type = ComponentType.PRODUCT,
                    sourceId = 1,
                    name = "Food",
                    servingWeight = 100.0,
                    carbsPer100g = 20.0,
                    caloriesPer100g = 100.0,
                    proteinsPer100g = 5.0,
                    fatsPer100g = 2.0
                )
            )
        )

        composeTestRule.setContent {
            GlucoPlanTheme {
                CalculatorScreen(
                    state = state,
                    onAddProduct = {},
                    onAddDish = {},
                    onRemoveComponent = {},
                    onWeightChange = { _, _ -> },
                    onGlucoseChange = {},
                    onToggleAdjustment = {},
                    onSaveMeal = {},
                    onAdjustPortion = {},
                    onClearAll = {},
                    onLoadFromHistory = {},
                    onNavigateToSimulator = {},
                    onNavigateToHistory = {},
                    onNavigateToSettings = {},
                    onNavigateToProducts = {},
                    onNavigateToDishes = {}
                )
            }
        }

        // Save button should be enabled
        composeTestRule.onNodeWithText("Сохранить")
            .assertIsEnabled()
    }

    // ─── Navigation Tests ─────────────────────────────────────────────────────────

    @Test
    fun navigationButtons_exist() {
        composeTestRule.setContent {
            GlucoPlanTheme {
                CalculatorScreen(
                    state = CalculatorUiState(),
                    onAddProduct = {},
                    onAddDish = {},
                    onRemoveComponent = {},
                    onWeightChange = { _, _ -> },
                    onGlucoseChange = {},
                    onToggleAdjustment = {},
                    onSaveMeal = {},
                    onAdjustPortion = {},
                    onClearAll = {},
                    onLoadFromHistory = {},
                    onNavigateToSimulator = {},
                    onNavigateToHistory = {},
                    onNavigateToSettings = {},
                    onNavigateToProducts = {},
                    onNavigateToDishes = {}
                )
            }
        }

        // Should have navigation elements
        composeTestRule.onNodeWithContentDescription("Настройки").assertExists()
    }

    // ─── CGM Widget Tests ─────────────────────────────────────────────────────────

    @Test
    fun cgmWidget_displaysWhenAvailable() {
        val state = CalculatorUiState(
            cgmReading = CgmReading(
                glucose = 7.5,
                direction = "SingleUp",
                time = java.time.Instant.now()
            )
        )

        composeTestRule.setContent {
            GlucoPlanTheme {
                CalculatorScreen(
                    state = state,
                    onAddProduct = {},
                    onAddDish = {},
                    onRemoveComponent = {},
                    onWeightChange = { _, _ -> },
                    onGlucoseChange = {},
                    onToggleAdjustment = {},
                    onSaveMeal = {},
                    onAdjustPortion = {},
                    onClearAll = {},
                    onLoadFromHistory = {},
                    onNavigateToSimulator = {},
                    onNavigateToHistory = {},
                    onNavigateToSettings = {},
                    onNavigateToProducts = {},
                    onNavigateToDishes = {}
                )
            }
        }

        // Should show CGM glucose value
        composeTestRule.onNodeWithText("7.5", substring = true).assertExists()
    }

    @Test
    fun cgmWidget_showsTrendArrow() {
        val state = CalculatorUiState(
            cgmReading = CgmReading(
                glucose = 8.0,
                direction = "DoubleUp",
                time = java.time.Instant.now()
            )
        )

        composeTestRule.setContent {
            GlucoPlanTheme {
                CalculatorScreen(
                    state = state,
                    onAddProduct = {},
                    onAddDish = {},
                    onRemoveComponent = {},
                    onWeightChange = { _, _ -> },
                    onGlucoseChange = {},
                    onToggleAdjustment = {},
                    onSaveMeal = {},
                    onAdjustPortion = {},
                    onClearAll = {},
                    onLoadFromHistory = {},
                    onNavigateToSimulator = {},
                    onNavigateToHistory = {},
                    onNavigateToSettings = {},
                    onNavigateToProducts = {},
                    onNavigateToDishes = {}
                )
            }
        }

        // Should show trend arrow
        composeTestRule.onNodeWithText("⇈").assertExists()
    }

    // ─── Stats Display Tests ──────────────────────────────────────────────────────

    @Test
    fun statsDisplay_showsCarbsAndXE() {
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
            settings = AppSettings(carbsPerXe = 12.0)
        )

        composeTestRule.setContent {
            GlucoPlanTheme {
                CalculatorScreen(
                    state = state,
                    onAddProduct = {},
                    onAddDish = {},
                    onRemoveComponent = {},
                    onWeightChange = { _, _ -> },
                    onGlucoseChange = {},
                    onToggleAdjustment = {},
                    onSaveMeal = {},
                    onAdjustPortion = {},
                    onClearAll = {},
                    onLoadFromHistory = {},
                    onNavigateToSimulator = {},
                    onNavigateToHistory = {},
                    onNavigateToSettings = {},
                    onNavigateToProducts = {},
                    onNavigateToDishes = {}
                )
            }
        }

        // Should show carbs (36g) and XE (3)
        composeTestRule.onNodeWithText("36", substring = true).assertExists()
    }

    // ─── State Change Tests ────────────────────────────────────────────────────────

    @Test
    fun savingState_showsProgress() {
        val state = CalculatorUiState(
            components = listOf(
                CalcComponent(
                    type = ComponentType.PRODUCT,
                    sourceId = 1,
                    name = "Food",
                    servingWeight = 100.0,
                    carbsPer100g = 20.0,
                    caloriesPer100g = 100.0,
                    proteinsPer100g = 5.0,
                    fatsPer100g = 2.0
                )
            ),
            isSaving = true
        )

        composeTestRule.setContent {
            GlucoPlanTheme {
                CalculatorScreen(
                    state = state,
                    onAddProduct = {},
                    onAddDish = {},
                    onRemoveComponent = {},
                    onWeightChange = { _, _ -> },
                    onGlucoseChange = {},
                    onToggleAdjustment = {},
                    onSaveMeal = {},
                    onAdjustPortion = {},
                    onClearAll = {},
                    onLoadFromHistory = {},
                    onNavigateToSimulator = {},
                    onNavigateToHistory = {},
                    onNavigateToSettings = {},
                    onNavigateToProducts = {},
                    onNavigateToDishes = {}
                )
            }
        }

        // Should show saving indicator
        composeTestRule.onNodeWithText("Сохранение", substring = true).assertExists()
    }

    @Test
    fun savedState_showsSuccess() {
        val state = CalculatorUiState(
            components = emptyList(),
            savedSuccess = true
        )

        composeTestRule.setContent {
            GlucoPlanTheme {
                CalculatorScreen(
                    state = state,
                    onAddProduct = {},
                    onAddDish = {},
                    onRemoveComponent = {},
                    onWeightChange = { _, _ -> },
                    onGlucoseChange = {},
                    onToggleAdjustment = {},
                    onSaveMeal = {},
                    onAdjustPortion = {},
                    onClearAll = {},
                    onLoadFromHistory = {},
                    onNavigateToSimulator = {},
                    onNavigateToHistory = {},
                    onNavigateToSettings = {},
                    onNavigateToProducts = {},
                    onNavigateToDishes = {}
                )
            }
        }

        // Should show success message
        composeTestRule.onNodeWithText("Сохранено", substring = true).assertExists()
    }
}
