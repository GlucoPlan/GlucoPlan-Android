package com.glucoplan.app.core.logging

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Process
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * Uncaught exception handler that saves detailed crash reports to file
 */
class CrashHandler(
    private val context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler? = Thread.getDefaultUncaughtExceptionHandler()
) : Thread.UncaughtExceptionHandler {

    private val crashDir: File = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
        "GlucoPlan/crashes"
    )

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    init {
        if (!crashDir.exists()) {
            crashDir.mkdirs()
        }
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val crashReport = generateCrashReport(thread, throwable)
            saveCrashReport(crashReport)
            Timber.e(throwable, "CRASH: Uncaught exception in thread ${thread.name}")
        } catch (e: Exception) {
            // Last resort - can't do much
        }

        // Call the default handler
        defaultHandler?.uncaughtException(thread, throwable) ?: run {
            Process.killProcess(Process.myPid())
            System.exit(1)
        }
    }

    private fun generateCrashReport(thread: Thread, throwable: Throwable): String {
        return buildString {
            appendLine("╔══════════════════════════════════════════════════════════════════╗")
            appendLine("║                        GLUCOPLAN CRASH REPORT                      ║")
            appendLine("╚══════════════════════════════════════════════════════════════════╝")
            appendLine()

            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━ CRASH INFO ━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("Time: ${dateFormat.format(Date())}")
            appendLine("Thread: ${thread.name} (id=${thread.id})")
            appendLine()

            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━ DEVICE INFO ━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("Manufacturer: ${Build.MANUFACTURER}")
            appendLine("Model: ${Build.MODEL}")
            appendLine("Device: ${Build.DEVICE}")
            appendLine("Product: ${Build.PRODUCT}")
            appendLine("Brand: ${Build.BRAND}")
            appendLine()

            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━ SYSTEM INFO ━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Security Patch: ${Build.VERSION.SECURITY_PATCH}")
            appendLine("Build: ${Build.DISPLAY}")
            appendLine()

            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━ APP INFO ━━━━━━━━━━━━━━━━━━━━━━━━━━")
            try {
                val pm = context.packageManager
                val pi = pm.getPackageInfo(context.packageName, 0)
                appendLine("Package: ${context.packageName}")
                appendLine("Version: ${pi.versionName} (${if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode})")
            } catch (e: Exception) {
                appendLine("Package: ${context.packageName}")
            }
            appendLine("Debug: ${BuildConfig.DEBUG}")
            appendLine()

            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━ MEMORY INFO ━━━━━━━━━━━━━━━━━━━━━━━━━━")
            val runtime = Runtime.getRuntime()
            val maxMem = runtime.maxMemory() / 1024 / 1024
            val totalMem = runtime.totalMemory() / 1024 / 1024
            val freeMem = runtime.freeMemory() / 1024 / 1024
            val usedMem = totalMem - freeMem
            appendLine("Max Memory: ${maxMem}MB")
            appendLine("Total Memory: ${totalMem}MB")
            appendLine("Free Memory: ${freeMem}MB")
            appendLine("Used Memory: ${usedMem}MB")
            appendLine()

            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━ STACK TRACE ━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("Exception: ${throwable.javaClass.name}")
            appendLine("Message: ${throwable.message}")
            appendLine()

            // Main exception stack trace
            appendLine("--- Main Stack ---")
            throwable.stackTrace.forEach { frame ->
                appendLine("    at $frame")
            }

            // Suppressed exceptions
            throwable.suppressed?.forEachIndexed { index, suppressed ->
                appendLine()
                appendLine("--- Suppressed #$index: ${suppressed.javaClass.name}: ${suppressed.message} ---")
                suppressed.stackTrace.forEach { frame ->
                    appendLine("    at $frame")
                }
            }

            // Cause chain
            var cause: Throwable? = throwable.cause
            var causeIndex = 0
            while (cause != null && causeIndex < 10) {
                appendLine()
                appendLine("--- Caused by: ${cause.javaClass.name}: ${cause.message} ---")
                cause.stackTrace.forEach { frame ->
                    appendLine("    at $frame")
                }
                cause = cause.cause
                causeIndex++
            }

            appendLine()
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━ RECENT LOGS ━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendRecentLogs(this)

            appendLine()
            appendLine("══════════════════════════════════════════════════════════════════")
            appendLine("Please send this file to the developer for analysis.")
            appendLine("══════════════════════════════════════════════════════════════════")
        }
    }

    private fun appendRecentLogs(sb: StringBuilder) {
        try {
            val logDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "GlucoPlan/logs"
            )
            val logFiles = logDir.listFiles()?.filter { it.name.endsWith(".log") }?.sortedByDescending { it.lastModified() }

            if (logFiles.isNullOrEmpty()) {
                sb.appendLine("No log files found.")
                return
            }

            val latestLog = logFiles.first()
            val lines = latestLog.readLines().takeLast(200)
            sb.appendLine("Last 200 lines from ${latestLog.name}:")
            sb.appendLine()
            lines.forEach { sb.appendLine(it) }

        } catch (e: Exception) {
            sb.appendLine("Failed to read recent logs: ${e.message}")
        }
    }

    private fun saveCrashReport(report: String) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            val crashFile = File(crashDir, "crash_$timestamp.log")

            PrintWriter(FileWriter(crashFile, false)).use { writer ->
                writer.print(report)
            }

            // Also write to Timber
            Timber.e("Crash report saved to: ${crashFile.absolutePath}")

        } catch (e: Exception) {
            Timber.e(e, "Failed to save crash report")
        }
    }

    companion object {
        /**
         * Install the crash handler
         */
        fun install(context: Context) {
            val handler = CrashHandler(context)
            Thread.setDefaultUncaughtExceptionHandler(handler)
            Timber.d("CrashHandler installed")
        }

        /**
         * Get all crash report files
         */
        fun getCrashReports(context: Context): List<File> {
            val crashDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "GlucoPlan/crashes"
            )
            return crashDir.listFiles()?.filter { it.name.endsWith(".log") }?.sortedByDescending { it.lastModified() } ?: emptyList()
        }

        /**
         * Delete old crash reports (keep last 10)
         */
        fun cleanOldReports(context: Context, keepCount: Int = 10) {
            val reports = getCrashReports(context)
            if (reports.size > keepCount) {
                reports.drop(keepCount).forEach { it.delete() }
            }
        }
    }
}
