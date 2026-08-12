package io.homeassistant.companion.android.util

import android.os.Build
import io.homeassistant.companion.android.BuildConfig

/**
 * Describes the build and the device a failure happened on.
 *
 * A [android.os.strictmode.Violation] stack trace shows where a failure happened but not what it
 * happened on, and the answer usually decides whether a report is actionable: several known
 * violations only occur on a narrow range of API levels or on specific manufacturer ROMs.
 *
 * Only information about the build and the device is included, never anything about the user or
 * their servers, because this ends up in logs and in the saved crash file.
 */
internal fun deviceDiagnosticInfo(): String = buildString {
    appendLine("App: ${BuildConfig.APPLICATION_ID} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    appendLine("Build: ${BuildConfig.FLAVOR}/${BuildConfig.BUILD_TYPE}")
    appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
    appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
}
