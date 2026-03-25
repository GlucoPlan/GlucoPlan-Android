package com.glucoplan.app.core.logging

/**
 * Build configuration constants.
 * These values are overwritten by the build system during compilation.
 * This file provides fallback values for crash reporting before BuildConfig is generated.
 */
object BuildConfig {
    const val DEBUG: Boolean = true
    const val APPLICATION_ID: String = "com.glucoplan.app"
    const val BUILD_TYPE: String = "debug"
    const val VERSION_CODE: Int = 2
    const val VERSION_NAME: String = "0.2.0"
}
