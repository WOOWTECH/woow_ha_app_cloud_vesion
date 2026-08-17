package io.homeassistant.companion.android.common.data.woowpaas

/**
 * Single entry point to the WOOW PaaS backend used by the cloud onboarding flow.
 *
 * The flow is: [requestDeviceCode] to start the OAuth 2.0 device flow, [pollToken] until the user
 * authorized the application, then [provision] to ask for a Home Assistant instance and [getStatus]
 * until that instance is ready.
 *
 * Implementations perform their network calls on a background dispatcher, so every function is safe to
 * call from the main thread.
 */
interface WoowPaasRepository {

    /**
     * Starts the OAuth 2.0 device flow and returns the codes the user needs to authorize the application.
     *
     * @return a failure carrying an [ApiException] when the backend rejected the request or answered
     * something that cannot be read
     */
    suspend fun requestDeviceCode(): Result<DeviceCodeResponse>

    /**
     * Polls the token endpoint once.
     *
     * Never throws for network or protocol problems: everything is reported through [TokenPollResult] so
     * the caller can decide whether to keep polling. A cancellation of the calling coroutine is not
     * swallowed and propagates as usual.
     *
     * @param deviceCode the code obtained from [requestDeviceCode]
     * @param currentInterval the polling interval in seconds currently in use, used to compute
     * [TokenPollResult.SlowDown]
     */
    suspend fun pollToken(deviceCode: String, currentInterval: Int): TokenPollResult

    /**
     * Asks the backend to provision the Home Assistant instance of the authenticated account.
     *
     * The call is idempotent on the backend side: asking again while an instance already exists reports
     * the state of that instance instead of failing.
     *
     * @param accessToken the token obtained from [pollToken]
     * @return a failure carrying an [ApiException] when the request was refused, for instance with a 401
     * when the token expired or a 403 when the `ha:provision` scope is missing
     */
    suspend fun provision(accessToken: String): Result<ProvisionResponse>

    /**
     * Queries the provisioning status of the authenticated account's Home Assistant instance.
     *
     * Unlike a plain `runCatching`, a cancellation is re-thrown rather than captured into a failed
     * [Result]: this function is called from a polling loop, and swallowing cancellation would turn a
     * cancelled poll into a spurious failure.
     *
     * @param accessToken the token obtained from [pollToken]
     */
    suspend fun getStatus(accessToken: String): Result<StatusResponse>
}
