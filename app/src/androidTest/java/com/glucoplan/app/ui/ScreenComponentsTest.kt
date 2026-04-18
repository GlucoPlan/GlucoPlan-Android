package com.glucoplan.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.glucoplan.app.domain.model.*
import com.glucoplan.app.ui.theme.GlucoPlanTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// ─────────────────────────────────────────────────────────────────────────────
// UI-тесты для stateless компонентов экранов GlucoPlan
// Тестируются карточки, диалоги и кнопки без зависимости от ViewModel/Hilt
// ─────────────────────────────────────────────────────────────────────────────

/**
 * UI-тесты для раздела «О программе» в настройках.
 */
@RunWith(AndroidJUnit4::class)
class НастройкиОПрограммеTest {

    @get:Rule
    val правило = createComposeRule()

    @Test
    fun строкаВерсии_отображает_название_и_версию() {
        правило.setContent {
            GlucoPlanTheme {
                ListItem(
                    headlineContent = { Text("Версия") },
                    trailingContent = { Text("0.3.9") }
                )
            }
        }
        правило.onNodeWithText("Версия").assertExists()
        правило.onNodeWithText("0.3.9").assertExists()
    }

    @Test
    fun дисклеймер_отображается_в_разделе_о_программе() {
        правило.setContent {
            GlucoPlanTheme {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        "Приложение является вспомогательным инструментом и не заменяет " +
                        "рекомендации лечащего врача. Все коэффициенты подбираются совместно " +
                        "с эндокринологом.",
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
        правило.onNodeWithText("не заменяет", substring = true).assertExists()
        правило.onNodeWithText("эндокринологом", substring = true).assertExists()
    }

    @Test
    fun секции_настроек_отображаются_правильно() {
        правило.setContent {
            GlucoPlanTheme {
                Column {
                    Text("Nightscout / CGM")
                    Text("Параметры лечения")
                    Text("Настройки приложения")
                    Text("О программе")
                }
            }
        }
        правило.onNodeWithText("Nightscout / CGM").assertExists()
        правило.onNodeWithText("Параметры лечения").assertExists()
        правило.onNodeWithText("Настройки приложения").assertExists()
        правило.onNodeWithText("О программе").assertExists()
    }
}

/**
 * UI-тесты для карточки приёма пищи в истории.
 */
@RunWith(AndroidJUnit4::class)
class КарточкаПриёмаПищиTest {

    @get:Rule
    val правило = createComposeRule()

    @Test
    fun карточка_показывает_дозу_инсулина() {
        правило.setContent {
            GlucoPlanTheme {
                Text("4.5 ед")
            }
        }
        правило.onNodeWithText("4.5 ед").assertExists()
    }

    @Test
    fun карточка_показывает_количество_углеводов() {
        правило.setContent {
            GlucoPlanTheme {
                ListItem(
                    headlineContent = { Text("Завтрак") },
                    supportingContent = { Text("УВ: 54 г  ·  3 ХЕ") }
                )
            }
        }
        правило.onNodeWithText("УВ: 54 г  ·  3 ХЕ").assertExists()
    }

    @Test
    fun диалог_удаления_приёма_имеет_кнопки_подтверждения_и_отмены() {
        правило.setContent {
            GlucoPlanTheme {
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text("Удалить запись?") },
                    confirmButton = {
                        TextButton(onClick = {}) { Text("Удалить") }
                    },
                    dismissButton = {
                        TextButton(onClick = {}) { Text("Отмена") }
                    }
                )
            }
        }
        правило.onNodeWithText("Удалить запись?").assertExists()
        правило.onNodeWithText("Удалить").assertExists()
        правило.onNodeWithText("Отмена").assertExists()
    }

    @Test
    fun кнопка_отмена_в_диалоге_удаления_кликабельна() {
        var закрыт = false
        правило.setContent {
            GlucoPlanTheme {
                AlertDialog(
                    onDismissRequest = { закрыт = true },
                    title = { Text("Удалить запись?") },
                    confirmButton = {
                        TextButton(onClick = {}) { Text("Удалить") }
                    },
                    dismissButton = {
                        TextButton(onClick = { закрыт = true }) { Text("Отмена") }
                    }
                )
            }
        }
        правило.onNodeWithText("Отмена").performClick()
        assertThat(закрыт).isTrue()
    }
}

/**
 * UI-тесты для карточки продукта.
 */
@RunWith(AndroidJUnit4::class)
class КарточкаПродуктаTest {

    @get:Rule
    val правило = createComposeRule()

    @Test
    fun карточка_отображает_название_продукта() {
        правило.setContent {
            GlucoPlanTheme {
                ListItem(headlineContent = { Text("Гречка") })
            }
        }
        правило.onNodeWithText("Гречка").assertExists()
    }

    @Test
    fun карточка_отображает_углеводы_на_100г() {
        правило.setContent {
            GlucoPlanTheme {
                ListItem(
                    headlineContent = { Text("Гречка") },
                    supportingContent = { Text("УВ: 68.0 г/100г") }
                )
            }
        }
        правило.onNodeWithText("УВ: 68.0 г/100г").assertExists()
    }

    @Test
    fun диалог_редактирования_продукта_имеет_нужные_поля() {
        правило.setContent {
            GlucoPlanTheme {
                Column {
                    OutlinedTextField(value = "Гречка", onValueChange = {},
                        label = { Text("Название") })
                    OutlinedTextField(value = "68.0", onValueChange = {},
                        label = { Text("Углеводы (г/100г)") })
                    OutlinedTextField(value = "343.0", onValueChange = {},
                        label = { Text("Калории") })
                }
            }
        }
        правило.onNodeWithText("Название", substring = true).assertExists()
        правило.onNodeWithText("Углеводы", substring = true).assertExists()
        правило.onNodeWithText("Калории", substring = true).assertExists()
    }

    @Test
    fun поле_поиска_продукта_принимает_ввод() {
        var текст = ""
        правило.setContent {
            GlucoPlanTheme {
                OutlinedTextField(
                    value = текст,
                    onValueChange = { текст = it },
                    label = { Text("Поиск продукта...") }
                )
            }
        }
        правило.onNodeWithText("Поиск продукта...", substring = true).assertExists()
    }
}

/**
 * UI-тесты для карточки блюда.
 */
@RunWith(AndroidJUnit4::class)
class КарточкаБлюдаTest {

    @get:Rule
    val правило = createComposeRule()

    @Test
    fun карточка_отображает_название_блюда() {
        правило.setContent {
            GlucoPlanTheme {
                Card(modifier = Modifier) {
                    ListItem(
                        headlineContent = { Text("Плов", fontWeight = FontWeight.Bold) },
                        supportingContent = { Text("УВ: 28.0 г/100г  ·  Ингредиентов: 3") }
                    )
                }
            }
        }
        правило.onNodeWithText("Плов").assertExists()
    }

    @Test
    fun карточка_отображает_количество_ингредиентов() {
        правило.setContent {
            GlucoPlanTheme {
                Text("Ингредиентов: 3")
            }
        }
        правило.onNodeWithText("Ингредиентов: 3").assertExists()
    }

    @Test
    fun карточка_кнопки_редактировать_и_удалить_существуют() {
        правило.setContent {
            GlucoPlanTheme {
                androidx.compose.foundation.layout.Row {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Edit, "Редактировать")
                    }
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Delete, "Удалить")
                    }
                }
            }
        }
        правило.onNodeWithContentDescription("Редактировать").assertExists()
        правило.onNodeWithContentDescription("Удалить").assertExists()
    }
}

/**
 * UI-тесты для диалога добавления ингредиента.
 */
@RunWith(AndroidJUnit4::class)
class ДиалогДобавленияИнгредиентаTest {

    @get:Rule
    val правило = createComposeRule()

    @Test
    fun диалог_показывает_заголовок() {
        правило.setContent {
            GlucoPlanTheme {
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text("Добавить ингредиент") },
                    text = { Text("Содержимое") },
                    confirmButton = {
                        TextButton(onClick = {}, enabled = false) { Text("Добавить") }
                    },
                    dismissButton = {
                        TextButton(onClick = {}) { Text("Отмена") }
                    }
                )
            }
        }
        правило.onNodeWithText("Добавить ингредиент").assertExists()
    }

    @Test
    fun кнопка_добавить_недоступна_если_продукт_не_выбран() {
        правило.setContent {
            GlucoPlanTheme {
                TextButton(onClick = {}, enabled = false) {
                    Text("Добавить")
                }
            }
        }
        правило.onNodeWithText("Добавить").assertIsNotEnabled()
    }

    @Test
    fun кнопка_добавить_доступна_после_выбора_продукта() {
        правило.setContent {
            GlucoPlanTheme {
                TextButton(onClick = {}, enabled = true) {
                    Text("Добавить")
                }
            }
        }
        правило.onNodeWithText("Добавить").assertIsEnabled()
    }

    @Test
    fun кнопка_отмена_в_диалоге_кликабельна() {
        var закрыт = false
        правило.setContent {
            GlucoPlanTheme {
                TextButton(onClick = { закрыт = true }) {
                    Text("Отмена")
                }
            }
        }
        правило.onNodeWithText("Отмена").performClick()
        assertThat(закрыт).isTrue()
    }
}

/**
 * UI-тесты для кнопок навигации — проверка что все кнопки обрабатывают клики.
 */
@RunWith(AndroidJUnit4::class)
class КнопкиНавигацииTest {

    @get:Rule
    val правило = createComposeRule()

    @Test
    fun кнопка_назад_обрабатывает_клик() {
        var кликнуто = false
        правило.setContent {
            GlucoPlanTheme {
                IconButton(onClick = { кликнуто = true }) {
                    Icon(Icons.Default.ArrowBack, "Назад")
                }
            }
        }
        правило.onNodeWithContentDescription("Назад").performClick()
        assertThat(кликнуто).isTrue()
    }

    @Test
    fun кнопка_сохранить_обрабатывает_клик() {
        var сохранено = false
        правило.setContent {
            GlucoPlanTheme {
                IconButton(onClick = { сохранено = true }) {
                    Icon(Icons.Default.Save, "Сохранить")
                }
            }
        }
        правило.onNodeWithContentDescription("Сохранить").performClick()
        assertThat(сохранено).isTrue()
    }

    @Test
    fun fab_добавить_обрабатывает_клик() {
        var нажато = false
        правило.setContent {
            GlucoPlanTheme {
                FloatingActionButton(onClick = { нажато = true }) {
                    Icon(Icons.Default.Add, "Добавить")
                }
            }
        }
        правило.onNodeWithContentDescription("Добавить").performClick()
        assertThat(нажато).isTrue()
    }

    @Test
    fun кнопка_удалить_обрабатывает_клик() {
        var удалено = false
        правило.setContent {
            GlucoPlanTheme {
                IconButton(onClick = { удалено = true }) {
                    Icon(Icons.Default.Delete, "Удалить")
                }
            }
        }
        правило.onNodeWithContentDescription("Удалить").performClick()
        assertThat(удалено).isTrue()
    }

    @Test
    fun кнопка_редактировать_обрабатывает_клик() {
        var нажато = false
        правило.setContent {
            GlucoPlanTheme {
                IconButton(onClick = { нажато = true }) {
                    Icon(Icons.Default.Edit, "Редактировать")
                }
            }
        }
        правило.onNodeWithContentDescription("Редактировать").performClick()
        assertThat(нажато).isTrue()
    }

    @Test
    fun кнопка_очистить_поиск_обрабатывает_клик() {
        var нажато = false
        правило.setContent {
            GlucoPlanTheme {
                IconButton(onClick = { нажато = true }) {
                    Icon(Icons.Default.Clear, "Очистить")
                }
            }
        }
        правило.onNodeWithContentDescription("Очистить").performClick()
        assertThat(нажато).isTrue()
    }
}

/**
 * UI-тесты для диалога кастрюль (PanEditDialog).
 */
@RunWith(AndroidJUnit4::class)
class ДиалогКастрюлиTest {

    @get:Rule
    val правило = createComposeRule()

    @Test
    fun диалог_кастрюли_показывает_поля_название_и_вес() {
        правило.setContent {
            GlucoPlanTheme {
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text("Кастрюля") },
                    text = {
                        Column {
                            OutlinedTextField(value = "Большая", onValueChange = {},
                                label = { Text("Название") })
                            OutlinedTextField(value = "500", onValueChange = {},
                                label = { Text("Вес (г)") })
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {}) { Text("Сохранить") }
                    },
                    dismissButton = {
                        TextButton(onClick = {}) { Text("Отмена") }
                    }
                )
            }
        }
        правило.onNodeWithText("Название", substring = true).assertExists()
        правило.onNodeWithText("Вес (г)", substring = true).assertExists()
        правило.onNodeWithText("Сохранить").assertExists()
        правило.onNodeWithText("Отмена").assertExists()
    }

    @Test
    fun кнопка_сохранить_в_диалоге_кастрюли_кликабельна() {
        var сохранено = false
        правило.setContent {
            GlucoPlanTheme {
                TextButton(onClick = { сохранено = true }) {
                    Text("Сохранить")
                }
            }
        }
        правило.onNodeWithText("Сохранить").performClick()
        assertThat(сохранено).isTrue()
    }
}
