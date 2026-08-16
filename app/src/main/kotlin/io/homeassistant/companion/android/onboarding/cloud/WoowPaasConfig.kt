package io.homeassistant.companion.android.onboarding.cloud

import io.homeassistant.companion.android.BuildConfig

/**
 * WOOW paas 環境設定。[BASE_URL]、[CLIENT_ID]、[SCOPES] 皆由 build type 對應的 [BuildConfig] 欄位提供
 * （見 build-logic 的 `AndroidApplicationConventionPlugin`），debug 走 stg、release 走 prod，
 * 不需要修改程式碼即可切換環境。
 */
internal object WoowPaasConfig {
    const val BASE_URL = BuildConfig.WOOW_PAAS_BASE_URL
    const val CLIENT_ID = BuildConfig.WOOW_PAAS_CLIENT_ID
    const val SCOPES = BuildConfig.WOOW_PAAS_SCOPES
    const val DEVICE_CODE_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:device_code"

    /**
     * Builds a full request URL by joining [BASE_URL] with [path], see [joinUrl] for the normalization
     * rules applied.
     */
    fun resolveUrl(path: String): String = joinUrl(BASE_URL, path)
}

/**
 * Joins [base] and [path] into a single URL, normalizing slashes so a trailing slash on [base] (prod
 * carries a `/woow` path prefix, not just a bare host) or a missing leading slash on [path] never
 * produces a malformed URL such as a double slash or a missing separator.
 */
internal fun joinUrl(base: String, path: String): String {
    val trimmedBase = base.trimEnd('/')
    val normalizedPath = if (path.startsWith('/')) path else "/$path"
    return "$trimmedBase$normalizedPath"
}
