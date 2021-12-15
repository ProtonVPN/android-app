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
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.api.VpnApiClient
import com.protonvpn.android.api.VpnApiManager
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.auth.usecase.OnSessionClosed
import com.protonvpn.android.components.NotificationHelper
import com.protonvpn.android.db.AppDatabase
import com.protonvpn.android.db.AppDatabase.Companion.buildDatabase
import com.protonvpn.android.di.AppDatabaseModule
import com.protonvpn.android.di.AppModuleProd
import com.protonvpn.android.di.UserManagerBindsModule
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.tv.login.TvLoginPollDelayMs
import com.protonvpn.android.tv.login.TvLoginViewModel
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.vpn.CertificateRepository
import com.protonvpn.android.vpn.MaintenanceTracker
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
import com.protonvpn.testsHelper.IdlingResourceHelper
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Main
import me.proton.core.network.data.ApiManagerFactory
import me.proton.core.network.data.ApiProvider
import me.proton.core.network.data.NetworkPrefs
import me.proton.core.network.data.ProtonCookieStore
import me.proton.core.network.domain.NetworkManager
import me.proton.core.network.domain.client.ClientIdProvider
import me.proton.core.network.domain.humanverification.HumanVerificationListener
import me.proton.core.network.domain.humanverification.HumanVerificationProvider
import me.proton.core.network.domain.server.ServerTimeListener
import me.proton.core.network.domain.session.SessionListener
import me.proton.core.network.domain.session.SessionProvider
import me.proton.core.user.data.repository.UserRepositoryImpl
import me.proton.core.user.domain.repository.PassphraseRepository
import me.proton.core.user.domain.repository.UserRepository
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [AppModuleProd::class]
)
class MockAppModule {

    private val scope = CoroutineScope(Main)

    @Singleton
    @Provides
    fun provideNetworkManager(): NetworkManager = MockNetworkManager()

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
        humanVerificationProvider: HumanVerificationProvider,
        humanVerificationListener: HumanVerificationListener,
        @ApplicationContext appContext: Context
    ): ApiManagerFactory {
        val serverTimeListener = object : ServerTimeListener {
            // We'd need to implement that when we start using core's crypto module.
            override fun onServerTimeUpdated(epochSeconds: Long) {}
        }
        val apiFactory = ApiManagerFactory(Constants.PRIMARY_VPN_API_URL, apiClient, clientIdProvider, serverTimeListener,
            networkManager, NetworkPrefs(appContext), sessionProvider, sessionListener, humanVerificationProvider,
            humanVerificationListener, cookieStore, scope, certificatePins = emptyArray(),
            alternativeApiPins = emptyList(), apiConnectionListener = null)

        val resource: IdlingResource =
            IdlingResourceHelper.create("OkHttp", apiFactory.baseOkHttpClient)
        IdlingRegistry.getInstance().register(resource)

        return apiFactory
    }

    @Singleton
    @Provides
    fun provideAPI(
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
        userData: UserData,
        backendManager: VpnBackendProvider,
        networkManager: NetworkManager,
        vpnConnectionErrorHandler: VpnConnectionErrorHandler,
        vpnStateMonitor: VpnStateMonitor,
        notificationHelper: NotificationHelper,
        serverManager: ServerManager,
        certificateRepository: CertificateRepository, // Make sure that CertificateRepository instance is created
        maintenanceTracker: MaintenanceTracker, // Make sure that MaintenanceTracker instance is created
    ): VpnConnectionManager =
            if (TestSettings.mockedConnectionUsed) {
                MockVpnConnectionManager(
                        userData,
                        backendManager,
                        networkManager,
                        vpnConnectionErrorHandler,
                        vpnStateMonitor,
                        notificationHelper,
                        serverManager,
                        scope,
                        System::currentTimeMillis
                )
            } else {
                VpnConnectionManager(
                        ProtonApplication.getAppContext(),
                        userData,
                        backendManager,
                        networkManager,
                        vpnConnectionErrorHandler,
                        vpnStateMonitor,
                        notificationHelper,
                        serverManager,
                        scope,
                        System::currentTimeMillis
                )
            }

    @Singleton
    @Provides
    fun provideVpnBackendManager(
        appConfig: AppConfig,
        serverManager: ServerManager,
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
                serverDeliver = serverManager,
                config = appConfig
        )
    } else {
        ProtonVpnBackendProvider(
                strongSwan = strongSwanBackend,
                openVpn = openVpnBackend,
                wireGuard = wireguardBackend,
                serverDeliver = serverManager,
                config = appConfig
        )
    }

    @Singleton
    @Provides
    fun provideRecentManager(
        scope: CoroutineScope,
        vpnStateMonitor: VpnStateMonitor,
        serverManager: ServerManager,
        onSessionClosed: OnSessionClosed
    ) = RecentsManager(scope, vpnStateMonitor, serverManager, onSessionClosed).apply { clear() }

    @Singleton
    @Provides
    @TvLoginPollDelayMs
    fun provideTvLoginPollDelayMs() = if (TestSettings.mockedConnectionUsed)
        TimeUnit.MILLISECONDS.toMillis(150) else TvLoginViewModel.POLL_DELAY_MS
}

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [AppDatabaseModule::class])
object AppDatabaseModuleTest {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            // Below are needed for room to work with InstantTaskExecutorRule
            .allowMainThreadQueries()
            .setTransactionExecutor(Executors.newSingleThreadExecutor())
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

