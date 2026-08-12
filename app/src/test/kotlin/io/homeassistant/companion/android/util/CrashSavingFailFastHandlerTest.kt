package io.homeassistant.companion.android.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.util.FailFastHandler
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [CrashSavingFailFastHandler] exists because `FailFast` terminates the process without throwing,
 * so these failures never reach the uncaught exception handler and would otherwise only ever exist
 * in logcat.
 *
 * Robolectric is required for a real `cacheDir`. Like every other Robolectric test here it must run
 * against [HiltTestApplication]: booting the real application would enable StrictMode and replace
 * the process wide `FailFast` handler with one that calls `exitProcess`, killing the test JVM for
 * every test that runs afterwards.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class CrashSavingFailFastHandlerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var delegate: RecordingFailFastHandler

    private val crashFile: File
        get() = File(context.cacheDir.absolutePath + "/fatalcrash/last_crash")

    @Before
    fun setup() {
        resetCrashSavingLatch()
        crashFile.delete()
        delegate = RecordingFailFastHandler()
    }

    @After
    fun tearDown() {
        resetCrashSavingLatch()
        crashFile.delete()
    }

    @Test
    fun `Given a failure when handled then it is recorded with its message and stack trace`() {
        CrashSavingFailFastHandler(context, delegate)
            .handleException(IllegalStateException("boom"), "extra context")

        val recorded = crashFile.readText()
        assertTrue(recorded.contains("extra context"))
        assertTrue(recorded.contains("java.lang.IllegalStateException: boom"))
    }

    @Test
    fun `Given a failure when handled then the delegate still runs`() {
        val throwable = IllegalStateException("boom")

        CrashSavingFailFastHandler(context, delegate).handleException(throwable, "extra context")

        assertEquals(listOf<Throwable>(throwable), delegate.handled)
    }

    @Test
    fun `Given a failure when handled then the build and device are recorded`() {
        CrashSavingFailFastHandler(context, delegate).handleException(IllegalStateException("boom"), null)

        val recorded = crashFile.readText()
        // Without these a report cannot be acted on: several known violations only happen on a
        // narrow range of API levels or on specific manufacturer ROMs.
        assertTrue(recorded.contains("App: "))
        assertTrue(recorded.contains("Device: "))
        assertTrue(recorded.contains("Android: "))
    }

    @Test
    fun `Given a failure already recorded when a second one is handled then the first is kept`() {
        val handler = CrashSavingFailFastHandler(context, delegate)

        handler.handleException(IllegalStateException(EARLIER_FAILURE), null)
        handler.handleException(IllegalStateException(LATER_FAILURE), null)

        val recorded = crashFile.readText()
        // The first failure is the one worth keeping; everything after it happens in an already
        // broken process. This also proves the latch cannot recurse.
        assertTrue(recorded.contains(EARLIER_FAILURE))
        assertTrue(!recorded.contains(LATER_FAILURE))
    }

    @Test
    fun `Given the second failure when handled then the delegate is still notified`() {
        val handler = CrashSavingFailFastHandler(context, delegate)

        handler.handleException(IllegalStateException(EARLIER_FAILURE), null)
        handler.handleException(IllegalStateException(LATER_FAILURE), null)

        // Only the recording is suppressed - the delegate decides what to do about every failure.
        assertEquals(2, delegate.handled.size)
    }

    private companion object {
        // Deliberately unlike any identifier in this file: the recorded stack trace contains the
        // name of the test method that created the exception, so plain words such as "second"
        // would match the method name rather than the failure being asserted on.
        const val EARLIER_FAILURE = "crash-alpha"
        const val LATER_FAILURE = "crash-beta"
    }

    private class RecordingFailFastHandler : FailFastHandler {
        val handled = mutableListOf<Throwable>()

        override fun handleException(throwable: Throwable, additionalMessage: String?) {
            handled += throwable
        }
    }
}
