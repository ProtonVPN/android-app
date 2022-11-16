/*
 * Copyright (c) 2018 Proton Technologies AG
 *
 * This file is part of ProtonVPN.
 *
 * ProtonVPN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonVPN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.protonvpn.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.protonvpn.TestSettings
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.api.VpnApiManager
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.appconfig.AppFeaturesPrefs
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.auth.usecase.OnSessionClosed
import com.protonvpn.android.concurrency.VpnDispatcherProvider
import com.protonvpn.android.db.AppDatabase
import com.protonvpn.android.db.AppDatabase.Companion.buildDatabase
import com.protonvpn.android.di.AppDatabaseModule
import com.protonvpn.android.di.AppModuleProd
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.vpn.usecase.GetConnectingDomain
import com.protonvpn.android.models.vpn.usecase.SupportsProtocol
import com.protonvpn.android.tv.login.TvLoginPollDelayMs
import com.protonvpn.android.tv.login.TvLoginViewModel
import com.protonvpn.android.ui.NewLookDialogProvider
import com.protonvpn.android.ui.home.GetNetZone
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.SharedPreferencesProvider
import com.protonvpn.android.vpn.CertRefreshScheduler
import com.protonvpn.android.vpn.CertificateRepository
import com.protonvpn.android.vpn.LocalAgentUnreachableTracker
import com.protonvpn.android.vpn.ProtocolSelection
import com.protonvpn.android.vpn.ProtonVpnBackendProvider
import com.protonvpn.android.vpn.RecentsManager
import com.protonvpn.android.vpn.VpnBackendProvider
import com.protonvpn.android.vpn.VpnPermissionDelegate
import com.protonvpn.android.vpn.VpnServicePermissionDelegate
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.openvpn.OpenVpnBackend
import com.protonvpn.android.vpn.wireguard.WireguardBackend
import com.protonvpn.mocks.MockUserRepository
import com.protonvpn.mocks.MockVpnBackend
import com.protonvpn.mocks.NoopCertRefreshScheduler
import com.protonvpn.test.shared.MockSharedPreferencesProvider
import com.protonvpn.testsHelper.EspressoDispatcherProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asExecutor
import me.proton.core.network.dagger.CoreBaseNetworkModule
import me.proton.core.network.data.di.AlternativeApiPins
import me.proton.core.network.data.di.BaseProtonApiUrl
import me.proton.core.network.data.di.CertificatePins
import me.proton.core.network.data.di.DohProviderUrls
import me.proton.core.network.data.di.SharedOkHttpClient
import me.proton.core.network.domain.NetworkManager
import me.proton.core.presentation.app.AppLifecycleObserver
import me.proton.core.presentation.app.AppLifecycleProvider
import me.proton.core.user.dagger.CoreUserRepositoriesModule
import me.proton.core.user.data.repository.DomainRepositoryImpl
import me.proton.core.user.data.repository.UserAddressRepositoryImpl
import me.proton.core.user.domain.repository.DomainRepository
import me.proton.core.user.domain.repository.PassphraseRepository
import me.proton.core.user.domain.repository.UserAddressRepository
import me.proton.core.user.domain.repository.UserRepository
import me.proton.core.util.android.dagger.CoreAndroidModule
import me.proton.core.util.kotlin.CoroutineScopeProvider
import me.proton.core.util.kotlin.DefaultCoroutineScopeProvider
import me.proton.core.util.kotlin.DispatcherProvider
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import me.proton.core.network.data.di.Constants as CoreConstants

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [AppModuleProd::class, CoreBaseNetworkModule::class]
)
class MockAppModule {

    // https://jira.protontech.ch/browse/CP-4334 - Provide an abstraction over `WorkManager`
    @Provides
    @Singleton
    fun provideWorkManager(): WorkManager = mockk()

    @Singleton
    @Provides
    fun provideNetworkManager(): NetworkManager = MockNetworkManager()

    @Provides
    @BaseProtonApiUrl
    fun provideProtonApiUrl(): HttpUrl = TestSettings.protonApiUrlOverride ?: Constants.PRIMARY_VPN_API_URL.toHttpUrl()

    @Provides
    @CertificatePins
    fun provideCertificatePins(): Array<String> = emptyArray()

    @Provides
    @AlternativeApiPins
    fun provideAlternativeApiPins(): List<String> = emptyList()

    @Provides
    @DohProviderUrls
    fun provideDohProviderUrls(): Array<String> = CoreConstants.DOH_PROVIDERS_URLS

    @Provides
    @Singleton
    @SharedOkHttpClient
    internal fun provideOkHttpClient(): OkHttpClient {
        val clientBuilder = OkHttpClient.Builder()
        TestSettings.handshakeCertificatesOverride?.let {
            clientBuilder.sslSocketFactory(it.sslSocketFactory(), it.trustManager)
        }
        return clientBuilder.build()
    }

    @Singleton
    @Provides
    fun provideAPI(
        scope: CoroutineScope,
        apiManager: VpnApiManager,
    ): ProtonApiRetroFit = ProtonApiRetroFit(scope, apiManager, null)

    @Singleton
    @Provides
    fun provideUserPrefs(appFeaturesPrefs: AppFeaturesPrefs): UserData = UserData.create(appFeaturesPrefs).apply {
        protocol = ProtocolSelection(VpnProtocol.WireGuard)
    }

    @Provides
    fun provideVpnPrepareDelegate(@ApplicationContext context: Context): VpnPermissionDelegate =
        if (TestSettings.mockedConnectionUsed) {
            VpnPermissionDelegate { null }
        } else {
            VpnServicePermissionDelegate(context)
        }

    @Singleton
    @Provides
    fun provideVpnBackendManager(
        scope: CoroutineScope,
        dispatcherProvider: DispatcherProvider,
        appConfig: AppConfig,
        networkManager: NetworkManager,
        certificateRepository: CertificateRepository,
        userData: UserData,
        openVpnBackend: OpenVpnBackend,
        wireguardBackend: WireguardBackend,
        localAgentUnreachableTracker: LocalAgentUnreachableTracker,
        currentUser: CurrentUser,
        getNetZone: GetNetZone,
        supportsProtocol: SupportsProtocol,
        getConnectingDomain: GetConnectingDomain
    ): VpnBackendProvider =
        if (TestSettings.mockedConnectionUsed) {
            ProtonVpnBackendProvider(
                openVpn = MockVpnBackend(
                    scope, dispatcherProvider, networkManager, certificateRepository, userData, appConfig,
                    VpnProtocol.OpenVPN, localAgentUnreachableTracker, currentUser, getNetZone, getConnectingDomain
                ),
                wireGuard = MockVpnBackend(
                    scope, dispatcherProvider, networkManager, certificateRepository, userData, appConfig,
                    VpnProtocol.WireGuard, localAgentUnreachableTracker, currentUser, getNetZone, getConnectingDomain
                ),
                config = appConfig,
                userData = userData,
                supportsProtocol = supportsProtocol
            )
        } else {
            ProtonVpnBackendProvider(
                openVpn = openVpnBackend,
                wireGuard = wireguardBackend,
                config = appConfig,
                userData = userData,
                supportsProtocol = supportsProtocol
            )
        }

    @Singleton
    @Provides
    fun provideRecentManager(
        scope: CoroutineScope,
        vpnStateMonitor: VpnStateMonitor,
        onSessionClosed: OnSessionClosed
    ) = RecentsManager(scope, vpnStateMonitor, onSessionClosed).apply { clear() }

    @Singleton
    @Provides
    @TvLoginPollDelayMs
    fun provideTvLoginPollDelayMs() = if (TestSettings.mockedConnectionUsed)
        TimeUnit.MILLISECONDS.toMillis(150) else TvLoginViewModel.POLL_DELAY_MS

    @Provides
    fun provideNewLookDialogProvider(): NewLookDialogProvider = object : NewLookDialogProvider() {
        override fun show(context: Context, tv: Boolean) {
            // Don't show the dialog in tests
        }
    }

    @Module
    @TestInstallIn(
        components = [SingletonComponent::class],
        replaces = [AppModuleProd.Bindings::class, CoreAndroidModule::class]
    )
    interface Bindings {
        @Binds
        fun bindCertificateRefreshSchedulers(scheduler: NoopCertRefreshScheduler): CertRefreshScheduler

        @Binds
        fun bindSharedPrefsProvider(provider: MockSharedPreferencesProvider): SharedPreferencesProvider

        @Binds
        @Singleton
        fun bindAppLifecycleProvider(impl: AppLifecycleObserver): AppLifecycleProvider

        @Binds
        @Singleton
        fun bindCoroutineScopeProvider(impl: DefaultCoroutineScopeProvider): CoroutineScopeProvider

        @Binds
        @Singleton
        fun provideDispatcherProvider(impl: VpnDispatcherProvider): DispatcherProvider

        @Binds
        @Singleton
        fun provideVpnDispatcherProvider(impl: EspressoDispatcherProvider): VpnDispatcherProvider
    }
}

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [AppDatabaseModule::class]
)
object AppDatabaseModuleTest {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        dispatcherProvider: DispatcherProvider
    ): AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            // Below are needed for room to work with InstantTaskExecutorRule
            .allowMainThreadQueries()
            .setTransactionExecutor(dispatcherProvider.Io.asExecutor())
            .buildDatabase()
}

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [CoreUserRepositoriesModule::class]
)
interface MockUserRepositoriesModule {
    @Binds
    @Singleton
    fun provideUserRepository(impl: MockUserRepository): UserRepository

    @Binds
    @Singleton
    fun providePassphraseRepository(impl: UserRepository): PassphraseRepository

    @Binds
    @Singleton
    fun provideDomainRepository(impl: DomainRepositoryImpl): DomainRepository

    @Binds
    @Singleton
    fun provideUserAddressRepository(impl: UserAddressRepositoryImpl): UserAddressRepository
}
