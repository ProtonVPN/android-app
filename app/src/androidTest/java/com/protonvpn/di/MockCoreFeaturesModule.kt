package com.protonvpn.di

import com.protonvpn.android.di.CoreNotificationFeaturesModule
import com.protonvpn.mocks.FakeIsAccountRecoveryEnabled
import com.protonvpn.mocks.FakeIsNotificationsEnabled
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import me.proton.core.accountrecovery.dagger.CoreAccountRecoveryFeaturesModule
import me.proton.core.accountrecovery.domain.IsAccountRecoveryEnabled
import me.proton.core.notification.domain.usecase.IsNotificationsEnabled
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [CoreAccountRecoveryFeaturesModule::class, CoreNotificationFeaturesModule::class]
)
interface MockCoreFeaturesModule {
    @Binds
    @Singleton
    fun bindIsAccountRecoveryEnabled(impl: FakeIsAccountRecoveryEnabled): IsAccountRecoveryEnabled

    @Binds
    @Singleton
    fun bindIsNotificationsEnabled(impl: FakeIsNotificationsEnabled): IsNotificationsEnabled

    companion object {
        @Provides
        @Singleton
        fun provideFakeIsAccountRecoveryEnabled() = FakeIsAccountRecoveryEnabled(enabled = false)

        @Provides
        @Singleton
        fun provideFakeIsNotificationsEnabled() = FakeIsNotificationsEnabled(enabled = false)
    }
}
