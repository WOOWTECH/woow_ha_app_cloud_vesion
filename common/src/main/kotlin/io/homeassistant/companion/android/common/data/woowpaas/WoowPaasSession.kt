package io.homeassistant.companion.android.common.data.woowpaas

import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * How long before its nominal expiry an access token is already considered unusable.
 *
 * A request started at the very end of the lifetime of a token would be refused by the time it reaches
 * the backend, so the token is retired early and refreshed instead.
 */
private val ACCESS_TOKEN_EXPIRY_LEEWAY = 1.minutes

/**
 * The credentials issued by WOOW PaaS for the account that authorized this device.
 *
 * The backend rotates the credentials on every refresh: a refresh returns a brand new access **and**
 * refresh token, and invalidates the ones that were used. A session is therefore replaced as a whole and
 * never updated field by field.
 *
 * @param accessToken the bearer token sent to the API endpoints, valid for one hour
 * @param refreshToken the token that buys a new session, valid for thirty days, absent when the backend
 * did not hand one out
 * @param accessTokenExpiresAt the instant [accessToken] stops being accepted
 */
@OptIn(ExperimentalTime::class)
data class WoowPaasSession(val accessToken: String, val refreshToken: String?, val accessTokenExpiresAt: Instant) {

    /**
     * Whether [accessToken] can still be used for a request started at [now].
     *
     * Tokens about to expire are reported as unusable so the caller refreshes ahead of time rather than
     * paying for a request that is going to be refused.
     */
    fun isAccessTokenUsableAt(now: Instant): Boolean = now < accessTokenExpiresAt - ACCESS_TOKEN_EXPIRY_LEEWAY

    /**
     * Whether this session can still authenticate a request started at [now], possibly after a refresh.
     *
     * When this is false the user has to go through the device flow again.
     */
    fun canAuthenticateAt(now: Instant): Boolean = isAccessTokenUsableAt(now) || refreshToken != null
}
