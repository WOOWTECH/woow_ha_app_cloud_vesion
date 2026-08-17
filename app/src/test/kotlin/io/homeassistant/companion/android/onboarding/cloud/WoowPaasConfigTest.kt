package io.homeassistant.companion.android.onboarding.cloud

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * Unit tests for [joinUrl], the URL-composition helper backing [WoowPaasConfig.resolveUrl].
 *
 * Naive string concatenation of a BASE_URL and a request path is fragile against trailing/leading
 * slash mismatches, and BASE_URL isn't guaranteed to be a bare host (see the `gateway.example.com`
 * cases below). These tests pin down the normalization so a misconfigured BASE_URL never silently
 * produces a malformed request URL.
 */
class WoowPaasConfigTest {

    @ParameterizedTest(name = "[{index}] base=\"{0}\" path=\"{1}\" -> \"{2}\"")
    @CsvSource(
        "https://stg.woowtech.io, /oauth2/device_authorization, https://stg.woowtech.io/oauth2/device_authorization",
        "https://paas.woowtech.io, /oauth2/device_authorization, https://paas.woowtech.io/oauth2/device_authorization",
        "https://stg.woowtech.io/, /oauth2/token, https://stg.woowtech.io/oauth2/token",
        "https://stg.woowtech.io, oauth2/token, https://stg.woowtech.io/oauth2/token",
        "https://gateway.example.com/api-prefix, /oauth2/device_authorization, https://gateway.example.com/api-prefix/oauth2/device_authorization",
        "https://gateway.example.com/api-prefix/, /api/ha-paas/status, https://gateway.example.com/api-prefix/api/ha-paas/status",
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
