package com.glucoplan.app.core.logging

import android.content.Context
import android.os.Environment
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Timber tree that writes logs to files in Documents/GlucoPlan/logs/
 * Does not require any special permissions on Android 10+
 */
class FileLogTree(
    private val context: Context,
    private val maxLogFiles: Int = 7,
    private val maxLogSizeBytes: Long = 2 * 1024 * 1024 // 2 MB per file
) : Timber.Tree() {

    private val logDir: File = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
        "GlucoPlan/logs"
    )

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val fileDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private val logQueue = ConcurrentLinkedQueue<String>()
    private val executor = Executors.newSingleThreadScheduledExecutor()

    private var currentLogFile: File? = null
    private var writer: PrintWriter? = null

    init {
        initLogDir()
        openLogFile()
        startFlushScheduler()

        // Flush on shutdown
        Runtime.getRuntime().addShutdownHook(Thread {
            flushAndClose()
        })
    }

    private fun initLogDir() {
        try {
            if (!logDir.exists()) {
                val created = logDir.mkdirs()
                Timber.d("Log directory created: $created, path: ${logDir.absolutePath}")
            }
            cleanOldLogs()
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize log directory")
        }
    }

    private fun cleanOldLogs() {
        val files = logDir.listFiles()?.filter { it.name.endsWith(".log") }?.sortedByDescending { it.lastModified() }
        if (files != null && files.size > maxLogFiles) {
            files.drop(maxLogFiles).forEach { file ->
                Timber.d("Deleting old log file: ${file.name}")
                file.delete()
            }
        }
    }

    private fun openLogFile() {
        try {
            val today = fileDateFormat.format(Date())
            val logFile = File(logDir, "glucoplan_$today.log")

            // Check file size and rotate if needed
            if (logFile.exists() && logFile.length() > maxLogSizeBytes) {
                val rotated = File(logDir, "glucoplan_${today}_${System.currentTimeMillis()}.log")
                logFile.renameTo(rotated)
            }

            currentLogFile = logFile
            writer = PrintWriter(FileWriter(logFile, true), true)
            logQueue.offer("=== Log session started at ${dateFormat.format(Date())} ===\n")
        } catch (e: Exception) {
            Timber.e(e, "Failed to open log file")
        }
    }

    private fun startFlushScheduler() {
        executor.scheduleWithFixedDelay({
            flushLogs()
        }, 1, 2, TimeUnit.SECONDS)
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val priorityChar = when (priority) {
            android.util.Log.VERBOSE -> 'V'
            android.util.Log.DEBUG -> 'D'
            android.util.Log.INFO -> 'I'
            android.util.Log.WARN -> 'W'
            android.util.Log.ERROR -> 'E'
            android.util.Log.ASSERT -> 'A'
            else -> '?'
        }

        val timestamp = dateFormat.format(Date())
        val threadName = Thread.currentThread().name

        val logLine = buildString {
            append("$timestamp [$priorityChar/$threadName] ")
            if (tag != null) append("$tag: ")
            append(message)
            if (t != null) {
                append("\n")
                append(android.util.Log.getStackTraceString(t))
            }
            append("\n")
        }

        logQueue.offer(logLine)
    }

    private fun flushLogs() {
        if (writer == null || logQueue.isEmpty()) return

        try {
            var line: String? = logQueue.poll()
            while (line != null) {
                writer?.print(line)
                line = logQueue.poll()
            }
            writer?.flush()
        } catch (e: Exception) {
            // Avoid recursive logging
        }
    }

    fun flushAndClose() {
        try {
            flushLogs()
            writer?.close()
            writer = null
            executor.shutdown()
        } catch (e: Exception) {
            // Ignore
        }
    }

}
