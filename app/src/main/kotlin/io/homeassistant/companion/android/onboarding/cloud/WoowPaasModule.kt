package io.homeassistant.companion.android.onboarding.cloud

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.homeassistant.companion.android.common.data.woowpaas.WoowPaasApiConfig
import io.homeassistant.companion.android.common.data.woowpaas.WoowPaasRepository
import io.homeassistant.companion.android.common.data.woowpaas.impl.WoowPaasRepositoryImpl
import javax.inject.Singleton

/**
 * Wires the WOOW PaaS client, which lives in `:common`, to the environment configuration owned by the
 * application module.
 *
 * Building the repository is cheap: its HTTP stack is created on first use, not here, so injecting it
 * into a view model does no work on the main thread.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object WoowPaasModule {

    @Provides
    @Singleton
    fun providesWoowPaasRepository(): WoowPaasRepository = WoowPaasRepositoryImpl(
        WoowPaasApiConfig(
            baseUrl = WoowPaasConfig.BASE_URL,
            clientId = WoowPaasConfig.CLIENT_ID,
            scopes = WoowPaasConfig.SCOPES,
            deviceCodeGrantType = WoowPaasConfig.DEVICE_CODE_GRANT_TYPE,
        ),
    )
}
