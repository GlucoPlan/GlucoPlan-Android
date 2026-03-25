package com.glucoplan.app

import android.app.Application
import com.glucoplan.app.core.logging.CrashHandler
import com.glucoplan.app.core.logging.FileLogTree
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class GlucoPlanApp : Application() {

    private var fileLogTree: FileLogTree? = null

    override fun onCreate() {
        super.onCreate()

        initLogging()
        initCrashHandler()

        Timber.i("═══════════════════════════════════════════════════════════════")
        Timber.i("GlucoPlan starting...")
        Timber.i("Version: ${com.glucoplan.app.core.logging.BuildConfig.VERSION_NAME}")
        Timber.i("Build: ${com.glucoplan.app.core.logging.BuildConfig.BUILD_TYPE}")
        Timber.i("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        Timber.i("Android: ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
        Timber.i("═══════════════════════════════════════════════════════════════")
    }

    private fun initLogging() {
        // Always plant file logger
        fileLogTree = FileLogTree(this).also { tree ->
            Timber.plant(tree)
        }

        // In debug builds, also log to Logcat
        if (com.glucoplan.app.core.logging.BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        Timber.d("Logging initialized. Log files in: Documents/GlucoPlan/logs/")
    }

    private fun initCrashHandler() {
        CrashHandler.install(this)
        CrashHandler.cleanOldReports(this, keepCount = 10)
        Timber.d("CrashHandler initialized")
    }

    override fun onTerminate() {
        Timber.i("GlucoPlan terminating...")
        fileLogTree?.flushAndClose()
        super.onTerminate()
    }
}
