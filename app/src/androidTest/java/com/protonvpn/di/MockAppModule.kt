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
import com.protonvpn.MockSwitch
import com.protonvpn.android.ProtonApplication
import com.protonvpn.android.api.HumanVerificationHandler
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.api.ProtonVPNRetrofit
import com.protonvpn.android.api.VpnApiClient
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.components.NotificationHelper
import com.protonvpn.android.db.AppDatabase
import com.protonvpn.android.db.AppDatabase.Companion.buildDatabase
import com.protonvpn.android.di.AppModuleProd
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.vpn.CertificateRepository
import com.protonvpn.android.vpn.MaintenanceTracker
import com.protonvpn.android.vpn.ProtonVpnBackendProvider
import com.protonvpn.android.vpn.VpnBackendProvider
import com.protonvpn.android.vpn.VpnConnectionErrorHandler
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.ikev2.StrongSwanBackend
import com.protonvpn.android.vpn.openvpn.OpenVpnBackend
import com.protonvpn.android.vpn.wireguard.WireguardBackend
import com.protonvpn.mocks.MockVpnBackend
import com.protonvpn.testsHelper.IdlingResourceHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Main
import me.proton.core.network.data.ApiManagerFactory
import me.proton.core.network.data.NetworkPrefs
import me.proton.core.network.data.ProtonCookieStore
import me.proton.core.network.domain.ApiManager
import me.proton.core.network.domain.NetworkManager
import me.proton.core.network.domain.client.ClientIdProvider
import me.proton.core.network.domain.server.ServerTimeListener
import java.util.Random
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [AppModuleProd::class]
)
class MockAppModule {

    private val scope = CoroutineScope(Main)
    private val random = Random()

    @Singleton
    @Provides
    fun provideNetworkManager(): NetworkManager = MockNetworkManager()

    @Singleton
    @Provides
    fun provideApiFactory(
        userData: UserData,
        networkManager: NetworkManager,
        apiClient: VpnApiClient,
        clientIdProvider: ClientIdProvider,
        humanVerificationHandler: HumanVerificationHandler,
        cookieStore: ProtonCookieStore,
    ): ApiManagerFactory {

        val appContext = ProtonApplication.getAppContext()
        val sessionProvider = userData.apiSessionProvider
        val serverTimeListener = object : ServerTimeListener {
            override fun onServerTimeUpdated(epochSeconds: Long) {}
        }
        val apiFactory = ApiManagerFactory(Constants.PRIMARY_VPN_API_URL, apiClient, clientIdProvider,
            serverTimeListener, networkManager, NetworkPrefs(appContext), sessionProvider, sessionProvider,
            humanVerificationHandler, humanVerificationHandler, cookieStore, scope)

        val resource: IdlingResource =
            IdlingResourceHelper.create("OkHttp", apiFactory.baseOkHttpClient)
        IdlingRegistry.getInstance().register(resource)

        return apiFactory
    }

    @Singleton
    @Provides
    fun provideAPI(apiManager: ApiManager<ProtonVPNRetrofit>, userData: UserData): ProtonApiRetroFit =
        if(MockSwitch.mockedConnectionUsed){
            MockApi(scope, apiManager, userData)
        } else{
            ProtonApiRetroFit(scope,apiManager)
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
            if(MockSwitch.mockedConnectionUsed) {
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
            }else {
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
        wireguardBackend: WireguardBackend
    ): VpnBackendProvider =
    if(MockSwitch.mockedConnectionUsed) {
        ProtonVpnBackendProvider(
                strongSwan = MockVpnBackend(scope, networkManager, certificateRepository, userData, appConfig,
                        VpnProtocol.IKEv2),
                openVpn = MockVpnBackend(scope, networkManager, certificateRepository, userData, appConfig,
                        VpnProtocol.OpenVPN),
                wireGuard = MockVpnBackend(scope, networkManager, certificateRepository, userData, appConfig,
                        VpnProtocol.WireGuard),
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
}

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [AppDatabaseModule::class])
object AppDatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).buildDatabase()
}