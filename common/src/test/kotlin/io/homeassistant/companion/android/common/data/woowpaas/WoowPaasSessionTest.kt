package io.homeassistant.companion.android.common.data.woowpaas

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private val NOW = Instant.fromEpochMilliseconds(0)

@OptIn(ExperimentalTime::class)
class WoowPaasSessionTest {

    @Test
    fun `Given an access token valid for another hour when checking it then it is usable`() {
        val session = session(remainingLifetime = 60.minutes)

        assertTrue(session.isAccessTokenUsableAt(NOW))
        assertTrue(session.canAuthenticateAt(NOW))
    }

    @Test
    fun `Given an access token that already expired when checking it then it is not usable`() {
        val session = session(remainingLifetime = -(1.minutes))

        assertFalse(session.isAccessTokenUsableAt(NOW))
    }

    @Test
    fun `Given an access token expiring within the leeway when checking it then it is already unusable`() {
        // A call started now would be refused mid-flight, so the token is treated as gone before the
        // backend actually drops it.
        val session = session(remainingLifetime = 30.seconds)

        assertFalse(session.isAccessTokenUsableAt(NOW))
    }

    @Test
    fun `Given an expired access token with a refresh token when checking it then it can still authenticate`() {
        val session = session(remainingLifetime = -(1.minutes), refreshToken = "refresh-1")

        assertTrue(session.canAuthenticateAt(NOW))
    }

    @Test
    fun `Given an expired access token without a refresh token when checking it then it cannot authenticate`() {
        val session = session(remainingLifetime = -(1.minutes), refreshToken = null)

        assertFalse(session.canAuthenticateAt(NOW))
    }

    private fun session(remainingLifetime: Duration, refreshToken: String? = "refresh-1") = WoowPaasSession(
        accessToken = "access-1",
        refreshToken = refreshToken,
        accessTokenExpiresAt = NOW + remainingLifetime,
    )
}
