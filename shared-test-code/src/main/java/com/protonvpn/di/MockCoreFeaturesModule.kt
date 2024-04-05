package com.protonvpn.di

import com.protonvpn.android.di.CoreNotificationFeaturesModule
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import me.proton.core.accountrecovery.dagger.CoreAccountRecoveryFeaturesModule
import me.proton.core.accountrecovery.domain.IsAccountRecoveryEnabled
import me.proton.core.accountrecovery.domain.IsAccountRecoveryResetEnabled
import me.proton.core.accountrecovery.test.fake.FakeIsAccountRecoveryEnabled
import me.proton.core.accountrecovery.test.fake.FakeIsAccountRecoveryResetEnabled
import me.proton.core.auth.dagger.CoreAuthFeaturesModule
import me.proton.core.auth.domain.usecase.IsCredentialLessEnabled
import me.proton.core.auth.domain.usecase.IsSsoCustomTabEnabled
import me.proton.core.auth.domain.usecase.IsSsoEnabled
import me.proton.core.auth.test.fake.FakeIsCredentialLessEnabled
import me.proton.core.auth.test.fake.FakeIsSsoCustomTabEnabled
import me.proton.core.auth.test.fake.FakeIsSsoEnabled
import me.proton.core.notification.domain.usecase.IsNotificationsEnabled
import me.proton.core.notification.test.fake.FakeIsNotificationsEnabled
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [
        CoreAccountRecoveryFeaturesModule::class,
        CoreAuthFeaturesModule::class,
        CoreNotificationFeaturesModule::class
    ]
)
interface MockCoreFeaturesModule {
    @Binds
    @Singleton
    fun bindIsAccountRecoveryEnabled(impl: FakeIsAccountRecoveryEnabled): IsAccountRecoveryEnabled

    @Binds
    @Singleton
    fun bindIsAccountRecoveryResetEnabled(impl: FakeIsAccountRecoveryResetEnabled): IsAccountRecoveryResetEnabled

    @Binds
    @Singleton
    fun bindIsCredentialLessEnabled(impl: FakeIsCredentialLessEnabled): IsCredentialLessEnabled

    @Binds
    @Singleton
    fun bindIsSsoCustomTabEnabled(impl: FakeIsSsoCustomTabEnabled): IsSsoCustomTabEnabled

    @Binds
    @Singleton
    fun bindIsSsoEnabled(impl: FakeIsSsoEnabled): IsSsoEnabled

    @Binds
    @Singleton
    fun bindIsNotificationsEnabled(impl: FakeIsNotificationsEnabled): IsNotificationsEnabled

    companion object {
        @Provides
        @Singleton
        fun provideFakeIsSsoEnabled() = FakeIsSsoEnabled(enabled = true)

        // NOTE: Other Fake.. classes have a default `false` value.
    }
}
