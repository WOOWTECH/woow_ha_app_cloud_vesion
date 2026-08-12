package io.homeassistant.companion.android.util

import android.content.Context
import android.os.StrictMode
import androidx.annotation.VisibleForTesting
import io.homeassistant.companion.android.common.util.FailFast
import io.homeassistant.companion.android.common.util.FailFastHandler
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Final location of a file on the device is /data/data/io.homeassistant.companion.android<.debug>/cache/fatalcrash/last_crash
 */

private const val FATAL_CRASH_FILE = "/fatalcrash/last_crash"

/**
 * Latches on the first recorded failure and is never reset.
 *
 * The first failure is the one worth keeping: everything after it happens in an already broken
 * process, and [CrashFailFastHandler] terminates the process anyway. Never resetting this also
 * makes the guard actually re-entrant, so a failure raised while recording a failure - including
 * one raised by the recording itself - cannot loop.
 */
private val hasSavedCrash = AtomicBoolean(false)

/**
 * Clears the latch so that a test can exercise more than one failure per JVM.
 *
 * Production code must never call this: reopening the latch reintroduces the recursion it prevents.
 */
@VisibleForTesting
internal fun resetCrashSavingLatch() = hasSavedCrash.set(false)

fun initCrashSaving(context: Context) {
    val handler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
        saveCrash(context, thread.name, exception.stackTraceToString(), additionalMessage = null)

        // Send to crash handling and/or system (and crash)
        handler?.uncaughtException(thread, exception)
    }
}

/**
 * Records [FailFast] failures to the same file as uncaught exceptions, then lets [delegate] decide
 * what to do about them.
 *
 * [FailFast] never throws: in debug it terminates the process with `exitProcess` and in release it
 * only logs. Neither path unwinds through the handler installed by [initCrashSaving], so without
 * this wrapper a fail-fast failure exists nowhere but logcat and is lost as soon as the buffer
 * rotates.
 *
 * Must be registered with [FailFast.setHandler] before anything that can fail, in particular before
 * StrictMode is enabled.
 */
class CrashSavingFailFastHandler(private val context: Context, private val delegate: FailFastHandler) :
    FailFastHandler {
    override fun handleException(throwable: Throwable, additionalMessage: String?) {
        // The build and device are attached here rather than by the caller so that no shared code
        // has to know about them, which keeps common/ identical to upstream.
        val message = listOfNotNull(additionalMessage, deviceDiagnosticInfo()).joinToString("\n")
        saveCrash(context, Thread.currentThread().name, throwable.stackTraceToString(), message)
        delegate.handleException(throwable, additionalMessage)
    }
}

/**
 * Writes the first fatal failure of this process to disk.
 *
 * Two deliberate exceptions to the project conventions, both required by the context this runs in:
 *
 * - The disk access is blocking and happens on the calling thread. The process is usually about to
 *   be terminated by `exitProcess`, so moving the write to [Dispatchers.IO] would race with process
 *   death and lose the report. **Do not wrap this in a coroutine.**
 * - The catch is deliberately broad. Failing to record a report must never mask, or add to, the
 *   failure being reported.
 *
 * StrictMode disk policy is relaxed for the duration, otherwise recording a StrictMode violation
 * would itself raise one.
 */
private fun saveCrash(context: Context, threadName: String, stackTrace: String, additionalMessage: String?) {
    if (!hasSavedCrash.compareAndSet(false, true)) return

    val previousPolicy = StrictMode.allowThreadDiskWrites()
    try {
        val crashFile = File(context.applicationContext.cacheDir.absolutePath + FATAL_CRASH_FILE)
        if (!crashFile.exists()) {
            crashFile.parentFile?.mkdirs()
            crashFile.createNewFile()
        }

        crashFile.writeText(
            buildString {
                // SimpleDateFormat rather than kotlin.time.Clock: the injected Clock comes from
                // Hilt, which cannot be relied on while the process is failing.
                appendLine(
                    "Timestamp: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())}",
                )
                appendLine("Thread: $threadName")
                additionalMessage?.let { appendLine(it) }
                appendLine("Exception: $stackTrace")
            },
        )
    } catch (e: Exception) {
        Timber.w(e, "Tried saving fatal crash but encountered exception")
    } finally {
        StrictMode.setThreadPolicy(previousPolicy)
    }
}

suspend fun getLatestFatalCrash(context: Context): String? = withContext(Dispatchers.IO) {
    var toReturn: String? = null
    try {
        val crashFile = File(context.applicationContext.cacheDir.absolutePath + FATAL_CRASH_FILE)
        if (crashFile.exists() &&
            crashFile.lastModified() >= (System.currentTimeMillis() - TimeUnit.HOURS.toMillis(12))
        ) { // Existing, recent file
            toReturn = crashFile.readText().trim().ifBlank { null }
        }
    } catch (e: Exception) {
        Timber.e(e, "Encountered exception while reading crash log file")
    }
    return@withContext toReturn
}
