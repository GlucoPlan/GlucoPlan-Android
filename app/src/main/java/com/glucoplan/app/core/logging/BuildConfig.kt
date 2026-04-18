package com.glucoplan.app.core.logging

/**
 * Прокси к автогенерированному BuildConfig.
 * Версия, applicationId и buildType берутся из app/build.gradle.kts —
 * менять нужно только там, здесь ничего не трогать.
 *
 * Gradle генерирует com.glucoplan.app.BuildConfig при каждой сборке.
 */
object BuildConfig {
    val DEBUG: Boolean          get() = com.glucoplan.app.BuildConfig.DEBUG
    val BUILD_TYPE: String      get() = com.glucoplan.app.BuildConfig.BUILD_TYPE

    val VERSION_NAME: String    get() = com.glucoplan.app.BuildConfig.VERSION_NAME
}
