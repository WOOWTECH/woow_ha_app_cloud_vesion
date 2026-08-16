package io.homeassistant.companion.android.onboarding.cloudprovision

import io.homeassistant.companion.android.common.data.woowpaas.ApiException
import io.homeassistant.companion.android.common.data.woowpaas.ProvisionResponse
import io.homeassistant.companion.android.common.data.woowpaas.ProvisionStatus
import io.homeassistant.companion.android.common.data.woowpaas.StatusResponse
import io.homeassistant.companion.android.common.data.woowpaas.WoowPaasRepository
import io.homeassistant.companion.android.onboarding.cloud.CloudOnboardingState
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.homeassistant.companion.android.testing.unit.FakeClock
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

private const val HA_URL = "https://ha.example.com"
private const val TEN_MINUTES_MS = 10 * 60 * 1000L

@ExtendWith(MainDispatcherJUnit5Extension::class, ConsoleLogExtension::class)
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class CloudProvisionViewModelTest {

    private val repository: WoowPaasRepository = mockk()

    companion object {
        @JvmStatic
        fun unexpectedProvisionStatuses(): List<Arguments> = listOf(
            Arguments.of(ProvisionStatus.None),
            Arguments.of(ProvisionStatus.Error),
            Arguments.of(ProvisionStatus.Unknown("hibernating")),
        )

        @JvmStatic
        fun abnormalPollingStatuses(): List<Arguments> = listOf(
            Arguments.of(ProvisionStatus.None),
            Arguments.of(ProvisionStatus.Unknown("hibernating")),
        )
    }

    @Test
    fun `Given transient status failures then ready when provisioning then it keeps polling until Ready`() = runTest {
        coEvery { repository.provision(any()) } returns Result.success(ProvisionResponse(status = ProvisionStatus.Provisioning))
        coEvery { repository.getStatus(any()) } returnsMany listOf(
            Result.failure(IOException("blip")),
            Result.failure(SocketTimeoutException("timeout")),
            Result.success(StatusResponse(status = ProvisionStatus.Ready, haUrl = HA_URL)),
        )
        val viewModel = viewModel()
        viewModel.setAccessToken(stateWithToken())

        viewModel.onProvisionClicked()
        advanceUntilIdle()

        assertEquals(ProvisionUiState.Ready(HA_URL), viewModel.uiState.value)
        coVerify(atLeast = 3) { repository.getStatus(any()) }
    }

    @Test
    fun `Given getStatus returns a terminal ApiException when provisioning then it stops with an Error`() = runTest {
        coEvery { repository.provision(any()) } returns Result.success(ProvisionResponse(status = ProvisionStatus.Provisioning))
        coEvery { repository.getStatus(any()) } returns Result.failure(ApiException(401, "登入已過期"))
        val viewModel = viewModel()
        viewModel.setAccessToken(stateWithToken())

        viewModel.onProvisionClicked()
        advanceUntilIdle()

        assertInstanceOf(ProvisionUiState.Error::class.java, viewModel.uiState.value)
        coVerify(exactly = 1) { repository.getStatus(any()) }
    }

    @Test
    fun `Given the network stays down when provisioning then it stops at the 10 minute timeout`() = runTest {
        val clock = FakeClock().apply { currentInstant = Instant.fromEpochMilliseconds(0) }
        coEvery { repository.provision(any()) } returns Result.success(ProvisionResponse(status = ProvisionStatus.Provisioning))
        coEvery { repository.getStatus(any()) } returns Result.failure(IOException("network down"))
        val viewModel = viewModel(clock)
        viewModel.setAccessToken(stateWithToken())

        viewModel.onProvisionClicked()
        runCurrent()

        // First status poll fails transiently while still inside the timeout budget: keep waiting.
        advanceTimeBy(5_500)
        runCurrent()
        assertInstanceOf(ProvisionUiState.Provisioning::class.java, viewModel.uiState.value)

        // Ten minutes elapse while still offline: the poll loop must give up.
        clock.currentInstant = Instant.fromEpochMilliseconds(TEN_MINUTES_MS + 1_000)
        advanceUntilIdle()

        assertInstanceOf(ProvisionUiState.Error::class.java, viewModel.uiState.value)
    }

    @Test
    fun `Given status becomes error when provisioning then it stops with the reported error`() = runTest {
        coEvery { repository.provision(any()) } returns Result.success(ProvisionResponse(status = ProvisionStatus.Provisioning))
        coEvery { repository.getStatus(any()) } returns Result.success(StatusResponse(status = ProvisionStatus.Error, error = "佈建失敗"))
        val viewModel = viewModel()
        viewModel.setAccessToken(stateWithToken())

        viewModel.onProvisionClicked()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertInstanceOf(ProvisionUiState.Error::class.java, state)
        assertEquals("佈建失敗", (state as ProvisionUiState.Error).message)
    }

    @Test
    fun `Given status ready without a url when provisioning then it stops with an Error`() = runTest {
        coEvery { repository.provision(any()) } returns Result.success(ProvisionResponse(status = ProvisionStatus.Provisioning))
        coEvery { repository.getStatus(any()) } returns Result.success(StatusResponse(status = ProvisionStatus.Ready, haUrl = null))
        val viewModel = viewModel()
        viewModel.setAccessToken(stateWithToken())

        viewModel.onProvisionClicked()
        advanceUntilIdle()

        assertInstanceOf(ProvisionUiState.Error::class.java, viewModel.uiState.value)
    }

    @Test
    fun `Given status provisioning then ready when provisioning then it reaches Ready`() = runTest {
        coEvery { repository.provision(any()) } returns Result.success(ProvisionResponse(status = ProvisionStatus.Provisioning))
        coEvery { repository.getStatus(any()) } returnsMany listOf(
            Result.success(StatusResponse(status = ProvisionStatus.Provisioning)),
            Result.success(StatusResponse(status = ProvisionStatus.Ready, haUrl = HA_URL)),
        )
        val viewModel = viewModel()
        viewModel.setAccessToken(stateWithToken())

        viewModel.onProvisionClicked()
        advanceUntilIdle()

        assertEquals(ProvisionUiState.Ready(HA_URL), viewModel.uiState.value)
    }

    @Test
    fun `Given provision returns ready immediately when provisioning then it goes straight to Ready without polling`() = runTest {
        coEvery { repository.provision(any()) } returns Result.success(ProvisionResponse(status = ProvisionStatus.Ready, haUrl = HA_URL))
        val viewModel = viewModel()
        viewModel.setAccessToken(stateWithToken())

        viewModel.onProvisionClicked()
        advanceUntilIdle()

        assertEquals(ProvisionUiState.Ready(HA_URL), viewModel.uiState.value)
        coVerify(exactly = 0) { repository.getStatus(any()) }
    }

    @Test
    fun `Given getStatus throws a CancellationException when provisioning then it propagates and does not become an Error`() = runTest {
        coEvery { repository.provision(any()) } returns Result.success(ProvisionResponse(status = ProvisionStatus.Provisioning))
        coEvery { repository.getStatus(any()) } throws CancellationException("cancelled during poll")
        val viewModel = viewModel()
        viewModel.setAccessToken(stateWithToken())

        viewModel.onProvisionClicked()
        advanceUntilIdle()

        assertInstanceOf(ProvisionUiState.Provisioning::class.java, viewModel.uiState.value)
    }

    @Test
    fun `Given getStatus yields a wrapped CancellationException when provisioning then it is not swallowed into an Error`() = runTest {
        coEvery { repository.provision(any()) } returns Result.success(ProvisionResponse(status = ProvisionStatus.Provisioning))
        coEvery { repository.getStatus(any()) } returns Result.failure(CancellationException("cancelled during poll"))
        val viewModel = viewModel()
        viewModel.setAccessToken(stateWithToken())

        viewModel.onProvisionClicked()
        advanceUntilIdle()

        assertInstanceOf(ProvisionUiState.Provisioning::class.java, viewModel.uiState.value)
    }

    @ParameterizedTest
    @MethodSource("unexpectedProvisionStatuses")
    fun `Given provision answers a state it never should when provisioning then the raw state is surfaced`(
        status: ProvisionStatus,
    ) = runTest {
        coEvery { repository.provision(any()) } returns Result.success(ProvisionResponse(status = status))
        val viewModel = viewModel()
        viewModel.setAccessToken(stateWithToken())

        viewModel.onProvisionClicked()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertInstanceOf(ProvisionUiState.Error::class.java, state)
        assertEquals("未預期的狀態: ${status.rawValue}", (state as ProvisionUiState.Error).message)
    }

    @ParameterizedTest
    @MethodSource("abnormalPollingStatuses")
    fun `Given the status becomes abnormal while polling then the raw state is surfaced`(status: ProvisionStatus) = runTest {
        coEvery { repository.provision(any()) } returns Result.success(
            ProvisionResponse(status = ProvisionStatus.Provisioning),
        )
        coEvery { repository.getStatus(any()) } returns Result.success(StatusResponse(status = status))
        val viewModel = viewModel()
        viewModel.setAccessToken(stateWithToken())

        viewModel.onProvisionClicked()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertInstanceOf(ProvisionUiState.Error::class.java, state)
        assertEquals("異常狀態：${status.rawValue}", (state as ProvisionUiState.Error).message)
    }

    @Test
    fun `Given the instance is suspended when polling then it stops on the Suspended screen`() = runTest {
        coEvery { repository.provision(any()) } returns Result.success(
            ProvisionResponse(status = ProvisionStatus.Provisioning),
        )
        coEvery { repository.getStatus(any()) } returns Result.success(
            StatusResponse(status = ProvisionStatus.Suspended),
        )
        val viewModel = viewModel()
        viewModel.setAccessToken(stateWithToken())

        viewModel.onProvisionClicked()
        advanceUntilIdle()

        assertEquals(ProvisionUiState.Suspended, viewModel.uiState.value)
    }

    @Test
    fun `Given provision reports a conflicting instance then it stops on the matching screen`() = runTest {
        coEvery { repository.provision(any()) } returns Result.success(
            ProvisionResponse(status = ProvisionStatus.Deleting, error = "instance is deleting"),
        )
        val viewModel = viewModel()
        viewModel.setAccessToken(stateWithToken())

        viewModel.onProvisionClicked()
        advanceUntilIdle()

        assertEquals(ProvisionUiState.Deleting, viewModel.uiState.value)
    }

    private fun viewModel(clock: Clock = FakeClock()) = CloudProvisionViewModel(repository, clock)

    private fun stateWithToken() = CloudOnboardingState().apply { accessToken = "token-123" }
}
