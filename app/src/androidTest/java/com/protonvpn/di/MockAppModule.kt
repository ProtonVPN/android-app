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
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.IdlingResource
import com.protonvpn.TestSettings
import com.protonvpn.android.ProtonApplication
import com.protonvpn.android.api.GuestHole
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.api.VpnApiClient
import com.protonvpn.android.api.VpnApiManager
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.auth.usecase.OnSessionClosed
import com.protonvpn.android.concurrency.VpnDispatcherProvider
import com.protonvpn.android.db.AppDatabase
import com.protonvpn.android.db.AppDatabase.Companion.buildDatabase
import com.protonvpn.android.di.AppDatabaseModule
import com.protonvpn.android.di.AppModuleProd
import com.protonvpn.android.di.UserManagerBindsModule
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.notifications.NotificationHelper
import com.protonvpn.android.tv.login.TvLoginPollDelayMs
import com.protonvpn.android.tv.login.TvLoginViewModel
import com.protonvpn.android.ui.NewLookDialogProvider
import com.protonvpn.android.ui.vpn.VpnBackgroundUiDelegate
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.SharedPreferencesProvider
import com.protonvpn.android.vpn.CertRefreshScheduler
import com.protonvpn.android.vpn.CertificateRepository
import com.protonvpn.android.vpn.ProtonVpnBackendProvider
import com.protonvpn.android.vpn.RecentsManager
import com.protonvpn.android.vpn.VpnBackendProvider
import com.protonvpn.android.vpn.VpnConnectionErrorHandler
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.ikev2.StrongSwanBackend
import com.protonvpn.android.vpn.openvpn.OpenVpnBackend
import com.protonvpn.android.vpn.wireguard.WireguardBackend
import com.protonvpn.mocks.MockVpnBackend
import com.protonvpn.mocks.NoopCertRefreshScheduler
import com.protonvpn.test.shared.MockSharedPreferencesProvider
import com.protonvpn.testsHelper.EspressoDispatcherProvider
import com.protonvpn.testsHelper.IdlingResourceHelper
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asExecutor
import me.proton.core.network.data.ApiManagerFactory
import me.proton.core.network.data.ApiProvider
import me.proton.core.network.data.NetworkPrefsImpl
import me.proton.core.network.data.ProtonCookieStore
import me.proton.core.network.domain.NetworkManager
import me.proton.core.network.domain.NetworkPrefs
import me.proton.core.network.domain.client.ClientIdProvider
import me.proton.core.network.domain.client.ClientVersionValidator
import me.proton.core.network.domain.client.ExtraHeaderProvider
import me.proton.core.network.domain.humanverification.HumanVerificationListener
import me.proton.core.network.domain.humanverification.HumanVerificationProvider
import me.proton.core.network.domain.scopes.MissingScopeListener
import me.proton.core.network.domain.server.ServerTimeListener
import me.proton.core.network.domain.session.SessionListener
import me.proton.core.network.domain.session.SessionProvider
import me.proton.core.user.data.repository.UserRepositoryImpl
import me.proton.core.user.domain.repository.PassphraseRepository
import me.proton.core.user.domain.repository.UserRepository
import me.proton.core.util.kotlin.DispatcherProvider
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [AppModuleProd::class]
)
class MockAppModule {

    @Provides
    @Singleton
    fun provideDispatcherProvider(): VpnDispatcherProvider = EspressoDispatcherProvider()

    @Singleton
    @Provides
    fun provideNetworkManager(): NetworkManager = MockNetworkManager()

    @Provides
    fun provideNetworkPrefs(@ApplicationContext context: Context): NetworkPrefs =
        NetworkPrefsImpl(context)

    @Singleton
    @Provides
    fun provideApiFactory(
        networkManager: NetworkManager,
        apiClient: VpnApiClient,
        clientIdProvider: ClientIdProvider,
        cookieStore: ProtonCookieStore,
        scope: CoroutineScope,
        sessionProvider: SessionProvider,
        sessionListener: SessionListener,
        missingScopeListener: MissingScopeListener,
        humanVerificationProvider: HumanVerificationProvider,
        humanVerificationListener: HumanVerificationListener,
        networkPrefs: NetworkPrefs,
        clientVersionValidator: ClientVersionValidator,
        guestHoleFallbackListener: GuestHole,
        extraHeaderProvider: ExtraHeaderProvider
    ): ApiManagerFactory {
        val serverTimeListener = object : ServerTimeListener {
            // We'd need to implement that when we start using core's crypto module.
            override fun onServerTimeUpdated(epochSeconds: Long) {}
        }
        val apiFactory = ApiManagerFactory(Constants.PRIMARY_VPN_API_URL, apiClient, clientIdProvider, serverTimeListener,
            networkManager, networkPrefs, sessionProvider, sessionListener, humanVerificationProvider,
            humanVerificationListener, missingScopeListener, cookieStore, scope, certificatePins = emptyArray(),
            alternativeApiPins = emptyList(),
            clientVersionValidator = clientVersionValidator,
            dohAlternativesListener = guestHoleFallbackListener,
            extraHeaderProvider = extraHeaderProvider
        )

        val resource: IdlingResource =
            IdlingResourceHelper.create("OkHttp", apiFactory.baseOkHttpClient)
        IdlingRegistry.getInstance().register(resource)

        return apiFactory
    }

    @Singleton
    @Provides
    fun provideAPI(
        scope: CoroutineScope,
        apiManager: VpnApiManager,
        apiProvider: ApiProvider,
        userData: UserData,
        currentUser: CurrentUser
    ): ProtonApiRetroFit =
        if (TestSettings.mockedConnectionUsed) {
            MockApi(scope, apiProvider, userData, currentUser)
        } else {
            ProtonApiRetroFit(scope, apiManager)
        }


    @Singleton
    @Provides
    fun provideUserPrefs(): UserData = UserData.create().apply {
        setProtocols(VpnProtocol.IKEv2, null)
    }

    @Singleton
    @Provides
    fun provideVpnConnectionManager(
        scope: CoroutineScope,
        userData: UserData,
        backendManager: VpnBackendProvider,
        networkManager: NetworkManager,
        vpnConnectionErrorHandler: VpnConnectionErrorHandler,
        vpnStateMonitor: VpnStateMonitor,
        vpnBackgroundUiDelegate: VpnBackgroundUiDelegate,
        serverManager: ServerManager,
        currentUser: CurrentUser,
        certificateRepository: CertificateRepository
    ): VpnConnectionManager =
            if (TestSettings.mockedConnectionUsed) {
                MockVpnConnectionManager(
                    userData,
                    backendManager,
                    networkManager,
                    vpnConnectionErrorHandler,
                    vpnStateMonitor,
                    vpnBackgroundUiDelegate,
                    serverManager,
                    certificateRepository,
                    scope,
                    System::currentTimeMillis,
                    currentUser
                )
            } else {
                VpnConnectionManager(
                    ProtonApplication.getAppContext(),
                    userData,
                    backendManager,
                    networkManager,
                    vpnConnectionErrorHandler,
                    vpnStateMonitor,
                    vpnBackgroundUiDelegate,
                    serverManager,
                    certificateRepository,
                    scope,
                    System::currentTimeMillis,
                    currentUser
                )
            }

    @Singleton
    @Provides
    fun provideVpnBackendManager(
        scope: CoroutineScope,
        appConfig: AppConfig,
        networkManager: NetworkManager,
        certificateRepository: CertificateRepository,
        userData: UserData,
        strongSwanBackend: StrongSwanBackend,
        openVpnBackend: OpenVpnBackend,
        wireguardBackend: WireguardBackend,
        currentUser: CurrentUser
    ): VpnBackendProvider =
    if (TestSettings.mockedConnectionUsed) {
        ProtonVpnBackendProvider(
                strongSwan = MockVpnBackend(scope, networkManager, certificateRepository, userData, appConfig,
                        VpnProtocol.IKEv2, currentUser),
                openVpn = MockVpnBackend(scope, networkManager, certificateRepository, userData, appConfig,
                        VpnProtocol.OpenVPN, currentUser),
                wireGuard = MockVpnBackend(scope, networkManager, certificateRepository, userData, appConfig,
                        VpnProtocol.WireGuard, currentUser),
                config = appConfig
        )
    } else {
        ProtonVpnBackendProvider(
                strongSwan = strongSwanBackend,
                openVpn = openVpnBackend,
                wireGuard = wireguardBackend,
                config = appConfig
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
        replaces = [AppModuleProd.Bindings::class]
    )
    interface Bindings {
        @Binds
        fun bindCertificateRefreshSchedulers(scheduler: NoopCertRefreshScheduler): CertRefreshScheduler

        @Binds
        fun bindSharedPrefsProvider(provider: MockSharedPreferencesProvider): SharedPreferencesProvider
    }
}

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [AppDatabaseModule::class])
object AppDatabaseModuleTest {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context, dispatcherProvider: DispatcherProvider): AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            // Below are needed for room to work with InstantTaskExecutorRule
            .allowMainThreadQueries()
            .setTransactionExecutor(dispatcherProvider.Io.asExecutor())
            .buildDatabase()
}

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [UserManagerBindsModule::class])
abstract class MockUserManagerBindsModule {

    @Binds
    abstract fun provideUserRepository(mockUserRepository: MockUserRepository): UserRepository

    @Binds
    abstract fun providePassphraseRepository(userRepositoryImpl: UserRepositoryImpl): PassphraseRepository
}

