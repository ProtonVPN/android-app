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
import me.proton.core.auth.domain.feature.IsCommonPasswordCheckEnabled
import me.proton.core.auth.domain.feature.IsCredentialLessEnabled
import me.proton.core.auth.domain.feature.IsFido2Enabled
import me.proton.core.auth.domain.feature.IsLoginTwoStepEnabled
import me.proton.core.auth.domain.feature.IsSsoCustomTabEnabled
import me.proton.core.auth.domain.feature.IsSsoEnabled
import me.proton.core.auth.test.fake.FakeIsCommonPasswordCheckEnabled
import me.proton.core.auth.test.fake.FakeIsCredentialLessEnabled
import me.proton.core.auth.test.fake.FakeIsFido2Enabled
import me.proton.core.auth.test.fake.FakeIsLoginTwoStepEnabled
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
    fun bindIsLoginTwoStepEnabled(impl: FakeIsLoginTwoStepEnabled): IsLoginTwoStepEnabled

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

    @Binds
    @Singleton
    fun bindIsCommonPasswordCheckEnabled(impl: FakeIsCommonPasswordCheckEnabled): IsCommonPasswordCheckEnabled

    @Binds
    @Singleton
    fun bindIsFido2Enabled(impl: FakeIsFido2Enabled): IsFido2Enabled

    companion object {
        @Provides
        @Singleton
        fun provideIsLoginTwoStepEnabled() = FakeIsLoginTwoStepEnabled(true)

        @Provides
        @Singleton
        fun provideIsAccountRecoveryEnabled() = FakeIsAccountRecoveryEnabled(true)

        @Provides
        @Singleton
        fun provideIsAccountRecoveryResetEnabled() = FakeIsAccountRecoveryResetEnabled(true)

        @Provides
        @Singleton
        fun provideIsCredentialLessEnabled() = FakeIsCredentialLessEnabled(true)

        @Provides
        @Singleton
        fun provideIsSsoCustomTabEnabled() = FakeIsSsoCustomTabEnabled(true)

        @Provides
        @Singleton
        fun provideIsSsoEnabled() = FakeIsSsoEnabled(true)

        @Provides
        @Singleton
        fun provideIsNotificationsEnabled() = FakeIsNotificationsEnabled(true)

        @Provides
        @Singleton
        fun provideIsCommonPasswordCheckEnabled() = FakeIsCommonPasswordCheckEnabled(true)

        @Provides
        @Singleton
        fun provideIsFido2Enabled() = FakeIsFido2Enabled(true)
    }
}
