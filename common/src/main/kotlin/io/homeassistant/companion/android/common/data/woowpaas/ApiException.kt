package io.homeassistant.companion.android.common.data.woowpaas

/**
 * A failure reported by the WOOW PaaS backend, carrying the HTTP status code that produced it.
 *
 * Callers use [code] to tell apart the cases that need a dedicated reaction (401 means the session is
 * gone, 403 means a missing scope, 503 means the service is not open yet) from the generic ones.
 */
class ApiException(val code: Int, message: String) : Exception(message)

/**
 * Placeholder [ApiException.code] used when the failure happened before any HTTP status was known,
 * for instance while decoding a malformed response body.
 */
const val HTTP_CODE_UNKNOWN: Int = -1
