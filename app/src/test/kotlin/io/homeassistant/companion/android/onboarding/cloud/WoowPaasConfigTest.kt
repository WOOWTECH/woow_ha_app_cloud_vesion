package io.homeassistant.companion.android.onboarding.cloud

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * Unit tests for [joinUrl], the URL-composition helper backing [WoowPaasConfig.resolveUrl].
 *
 * Prod's BASE_URL carries a path prefix (`/woow`) instead of being a bare host, so naive string
 * concatenation is fragile against trailing/leading slash mismatches. These tests pin down the
 * normalization so a misconfigured BASE_URL never silently produces a malformed request URL.
 */
class WoowPaasConfigTest {

    @ParameterizedTest(name = "[{index}] base=\"{0}\" path=\"{1}\" -> \"{2}\"")
    @CsvSource(
        "https://stg.woowtech.io, /oauth2/device_authorization, https://stg.woowtech.io/oauth2/device_authorization",
        "https://paas.woowtech.io/woow, /oauth2/device_authorization, https://paas.woowtech.io/woow/oauth2/device_authorization",
        "https://stg.woowtech.io/, /oauth2/token, https://stg.woowtech.io/oauth2/token",
        "https://stg.woowtech.io, oauth2/token, https://stg.woowtech.io/oauth2/token",
        "https://paas.woowtech.io/woow/, /api/ha-paas/status, https://paas.woowtech.io/woow/api/ha-paas/status",
    )
    fun `Given a BASE_URL and a request path when joinUrl then it normalizes slashes into a single well-formed URL`(
        base: String,
        path: String,
        expected: String,
    ) {
        assertEquals(expected, joinUrl(base, path))
    }

    @Test
    fun `Given WoowPaasConfig BASE_URL when resolveUrl then the path is appended to it`() {
        val result = WoowPaasConfig.resolveUrl("/oauth2/device_authorization")

        assertEquals("${WoowPaasConfig.BASE_URL}/oauth2/device_authorization", result)
    }
}
