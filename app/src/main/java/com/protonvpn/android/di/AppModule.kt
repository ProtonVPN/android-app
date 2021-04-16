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
package com.protonvpn.android.di

import android.os.SystemClock
import com.google.gson.Gson
import com.protonvpn.android.BuildConfig
import com.protonvpn.android.ProtonApplication
import com.protonvpn.android.api.VpnApiManager
import com.protonvpn.android.api.GuestHole
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.api.ProtonVPNRetrofit
import com.protonvpn.android.api.VpnApiClient
import com.protonvpn.android.appconfig.ApiNotificationManager
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.components.NotificationHelper
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.ui.home.LogoutHandler
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.utils.AndroidUtils.isTV
import com.protonvpn.android.utils.Constants.PRIMARY_VPN_API_URL
import com.protonvpn.android.utils.CoreLogger
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.utils.TrafficMonitor
import com.protonvpn.android.utils.UserPlanManager
import com.protonvpn.android.vpn.ConnectivityMonitor
import com.protonvpn.android.vpn.openvpn.OpenVpnBackend
import com.protonvpn.android.vpn.ProtonVpnBackendProvider
import com.protonvpn.android.vpn.MaintenanceTracker
import com.protonvpn.android.vpn.RecentsManager
import com.protonvpn.android.vpn.ikev2.StrongSwanBackend
import com.protonvpn.android.vpn.VpnBackendProvider
import com.protonvpn.android.vpn.VpnConnectionErrorHandler
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnErrorUIManager
import com.protonvpn.android.vpn.VpnStateMonitor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import me.proton.core.humanverification.data.repository.HumanVerificationRemoteRepositoryImpl
import me.proton.core.humanverification.domain.repository.HumanVerificationRemoteRepository
import me.proton.core.network.data.ApiProvider
import me.proton.core.network.data.ProtonCookieStore
import me.proton.core.network.data.di.ApiFactory
import me.proton.core.network.data.di.NetworkManager
import me.proton.core.network.data.di.NetworkPrefs
import me.proton.core.network.domain.ApiManager
import me.proton.core.network.domain.NetworkManager
import java.util.Random
import javax.inject.Singleton
import me.proton.core.country.data.repository.CountriesRepositoryImpl
import me.proton.core.country.domain.repository.CountriesRepository

@Module
class AppModule {

    private val scope = CoroutineScope(Dispatchers.Main)
    private val random = Random()

    @Singleton
    @Provides
    fun provideServerManager(userData: UserData) =
        ServerManager(ProtonApplication.getAppContext(), userData)

    @Singleton
    @Provides
    fun provideServerListUpdater(
        api: ProtonApiRetroFit,
        serverManager: ServerManager,
        userData: UserData,
        vpnStateMonitor: VpnStateMonitor,
        userPlanManager: UserPlanManager,
    ) = ServerListUpdater(scope, api, serverManager, userData, vpnStateMonitor, userPlanManager)

    @Singleton
    @Provides
    fun provideAppConfig(api: ProtonApiRetroFit, userData: UserData): AppConfig = AppConfig(scope, api, userData)

    @Singleton
    @Provides
    fun provideApiNotificationManager(appConfig: AppConfig): ApiNotificationManager =
        ApiNotificationManager(scope, System::currentTimeMillis, appConfig)

    @Singleton
    @Provides
    fun provideNetworkManager(): NetworkManager =
        NetworkManager(ProtonApplication.getAppContext())

    @Singleton
    @Provides
    fun provideVpnApiManager(userData: UserData, apiProvider: ApiProvider) =
        VpnApiManager(apiProvider, userData.apiSessionProvider)

    @Singleton
    @Provides
    fun provideApiFactory(
        userData: UserData,
        networkManager: NetworkManager,
        apiClient: VpnApiClient
    ): ApiFactory {
        val appContext = ProtonApplication.getAppContext()
        val logger = CoreLogger()
        val sessionProvider = userData.apiSessionProvider
        val cookieStore = ProtonCookieStore(appContext)
        return if (BuildConfig.DEBUG) {
            ApiFactory(PRIMARY_VPN_API_URL, apiClient, logger, networkManager,
                NetworkPrefs(appContext), sessionProvider, sessionProvider, cookieStore, scope,
                certificatePins = emptyArray(), alternativeApiPins = emptyList())
        } else {
            ApiFactory(PRIMARY_VPN_API_URL, apiClient, logger, networkManager,
                NetworkPrefs(appContext), sessionProvider, sessionProvider, cookieStore, scope)
        }
    }

    @Singleton
    @Provides
    fun provideApiProvider(apiFactory: ApiFactory, userData: UserData): ApiProvider =
        ApiProvider(apiFactory, userData.apiSessionProvider)

    @Singleton
    @Provides
    fun provideApiManager(
        vpnApiManager: VpnApiManager
    ): ApiManager<ProtonVPNRetrofit> = vpnApiManager

    @Singleton
    @Provides
    fun provideApiClient(userData: UserData, vpnStateMonitor: VpnStateMonitor): VpnApiClient =
        VpnApiClient(scope, userData, vpnStateMonitor)

    @Singleton
    @Provides
    fun provideAPI(apiManager: ApiManager<ProtonVPNRetrofit>) = ProtonApiRetroFit(scope, apiManager)

    @Singleton
    @Provides
    fun provideRecentManager(
        vpnStateMonitor: VpnStateMonitor,
        serverManager: ServerManager,
        logoutHandler: LogoutHandler
    ) = RecentsManager(scope, vpnStateMonitor, serverManager, logoutHandler)

    @Singleton
    @Provides
    fun provideGson() = Gson()

    @Singleton
    @Provides
    fun provideUserPrefs(): UserData = Storage.load(UserData::class.java, UserData())

    @Singleton
    @Provides
    fun provideUserPlanManager(
        api: ProtonApiRetroFit,
        userData: UserData,
        vpnStateMonitor: VpnStateMonitor,
    ): UserPlanManager = UserPlanManager(api, userData, vpnStateMonitor)

    @Singleton
    @Provides
    fun provideVpnConnectionErrorHandler(
        api: ProtonApiRetroFit,
        appConfig: AppConfig,
        userData: UserData,
        userPlanManager: UserPlanManager,
        serverManager: ServerManager,
        vpnStateMonitor: VpnStateMonitor,
        serverListUpdater: ServerListUpdater,
        errorUIManager: VpnErrorUIManager,
        networkManager: NetworkManager,
        vpnBackendProvider: VpnBackendProvider,
    ) = VpnConnectionErrorHandler(scope, ProtonApplication.getAppContext(), api, appConfig, userData, userPlanManager,
        serverManager, vpnStateMonitor, serverListUpdater, errorUIManager, networkManager, vpnBackendProvider)

    @Singleton
    @Provides
    fun provideVpnErrorUIManager(
        appConfig: AppConfig,
        userData: UserData,
        userPlanManager: UserPlanManager,
        vpnStateMonitor: VpnStateMonitor,
        notificationHelper: NotificationHelper,
    ) = VpnErrorUIManager(scope, ProtonApplication.getAppContext(), appConfig, userData, userPlanManager,
        vpnStateMonitor, notificationHelper)

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
        maintenanceTracker: MaintenanceTracker, // Make sure that MaintenanceTracker instance is created
    ) = VpnConnectionManager(
        ProtonApplication.getAppContext(),
        userData,
        backendManager,
        networkManager,
        vpnConnectionErrorHandler,
        vpnStateMonitor,
        notificationHelper,
        serverManager,
        scope,
    )

    @Singleton
    @Provides
    fun provideVpnStateMonitor() = VpnStateMonitor()

    @Singleton
    @Provides
    fun provideConnectivityMonitor() = ConnectivityMonitor(scope, ProtonApplication.getAppContext())

    @Singleton
    @Provides
    fun provideNotificationHelper(
        vpnStateMonitor: VpnStateMonitor,
        trafficMonitor: TrafficMonitor,
    ) = NotificationHelper(ProtonApplication.getAppContext(), scope, vpnStateMonitor, trafficMonitor)

    @Singleton
    @Provides
    fun provideVpnBackendManager(
        userData: UserData,
        networkManager: NetworkManager,
        appConfig: AppConfig,
        serverManager: ServerManager
    ): VpnBackendProvider =
        ProtonVpnBackendProvider(
            StrongSwanBackend(random, networkManager, scope, System::currentTimeMillis),
            OpenVpnBackend(random, userData, appConfig, System::currentTimeMillis),
            serverManager)

    @Singleton
    @Provides
    fun provideMaintenanceTracker(
        appConfig: AppConfig,
        vpnStateMonitor: VpnStateMonitor,
        vpnErrorHandler: VpnConnectionErrorHandler
    ) = MaintenanceTracker(scope, ProtonApplication.getAppContext(), appConfig, vpnStateMonitor, vpnErrorHandler)

    @Singleton
    @Provides
    fun provideTrafficMonitor(
        vpnStateMonitor: VpnStateMonitor,
        connectivityMonitor: ConnectivityMonitor
    ) = TrafficMonitor(
        ProtonApplication.getAppContext(),
        scope,
        SystemClock::elapsedRealtime,
        vpnStateMonitor,
        connectivityMonitor
    )

    @Singleton
    @Provides
    fun provideGuestHole(
        serverManager: ServerManager,
        vpnMonitor: VpnStateMonitor,
        connectionManager: VpnConnectionManager
    ) = GuestHole(scope, serverManager, vpnMonitor, connectionManager)

    @Singleton
    @Provides
    fun provideLogoutHandler(
        userData: UserData,
        serverManager: ServerManager,
        vpnApiManager: VpnApiManager,
        vpnStateMonitor: VpnStateMonitor,
        vpnConnectionManager: VpnConnectionManager,
        vpnApiClient: VpnApiClient
    ): LogoutHandler = LogoutHandler(scope, userData, serverManager, vpnApiManager, userData.apiSessionProvider,
        vpnStateMonitor, vpnConnectionManager, vpnApiClient)
}

@Module
@InstallIn(SingletonComponent::class)
object HiltAppModule {

    @Provides
    fun provideHumanVerificationRemoteRepository(apiProvider: ApiProvider): HumanVerificationRemoteRepository =
        HumanVerificationRemoteRepositoryImpl(apiProvider)

    @Provides
    fun provideCountriesRepository(): CountriesRepository =
        CountriesRepositoryImpl(ProtonApplication.getAppContext())
}
