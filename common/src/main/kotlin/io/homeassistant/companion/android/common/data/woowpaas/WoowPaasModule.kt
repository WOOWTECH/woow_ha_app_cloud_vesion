package io.homeassistant.companion.android.common.data.woowpaas

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.homeassistant.companion.android.common.data.woowpaas.impl.WoowPaasRepositoryImpl
import javax.inject.Singleton

/**
 * Binds the WOOW PaaS repository to its implementation.
 *
 * The environment it talks to is not bound here: a [WoowPaasApiConfig] is expected from the hosting
 * application, which owns the staging and production values. Applications that never inject a
 * [WoowPaasRepository] therefore do not need to provide one.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class WoowPaasModule {

    // Scoped so the lazily built HTTP stack inside the implementation is created at most once.
    @Binds
    @Singleton
    abstract fun bindWoowPaasRepository(impl: WoowPaasRepositoryImpl): WoowPaasRepository
}
