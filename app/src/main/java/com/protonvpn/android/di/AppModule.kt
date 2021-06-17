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
import com.protonvpn.android.api.GuestHole
import com.protonvpn.android.api.HumanVerificationHandler
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.api.ProtonVPNRetrofit
import com.protonvpn.android.api.VpnApiClient
import com.protonvpn.android.api.VpnApiManager
import com.protonvpn.android.appconfig.ApiNotificationManager
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.components.NotificationHelper
import com.protonvpn.android.concurrency.DefaultDispatcherProvider
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.ui.home.LogoutHandler
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.utils.Constants.PRIMARY_VPN_API_URL
import com.protonvpn.android.utils.CoreLogger
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.TrafficMonitor
import com.protonvpn.android.utils.UserPlanManager
import com.protonvpn.android.vpn.CertificateRepository
import com.protonvpn.android.vpn.ConnectivityMonitor
import com.protonvpn.android.vpn.MaintenanceTracker
import com.protonvpn.android.vpn.ProtonVpnBackendProvider
import com.protonvpn.android.vpn.RecentsManager
import com.protonvpn.android.vpn.VpnBackendProvider
import com.protonvpn.android.vpn.VpnConnectionErrorHandler
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnErrorUIManager
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.ikev2.StrongSwanBackend
import com.protonvpn.android.vpn.openvpn.OpenVpnBackend
import com.protonvpn.android.vpn.wireguard.WireguardBackend
import com.wireguard.android.backend.GoBackend
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import me.proton.core.country.data.repository.CountriesRepositoryImpl
import me.proton.core.country.domain.repository.CountriesRepository
import me.proton.core.humanverification.data.repository.UserVerificationRepositoryImpl
import me.proton.core.humanverification.domain.HumanVerificationWorkflowHandler
import me.proton.core.humanverification.domain.repository.UserVerificationRepository
import me.proton.core.humanverification.presentation.CaptchaBaseUrl
import me.proton.core.network.data.ApiManagerFactory
import me.proton.core.network.data.ApiProvider
import me.proton.core.network.data.NetworkManager
import me.proton.core.network.data.NetworkPrefs
import me.proton.core.network.data.ProtonCookieStore
import me.proton.core.network.data.client.ClientIdProviderImpl
import me.proton.core.network.domain.ApiManager
import me.proton.core.network.domain.NetworkManager
import me.proton.core.network.domain.client.ClientIdProvider
import me.proton.core.util.kotlin.DispatcherProvider
import java.util.Random
import javax.inject.Singleton

@Module
class AppModule {

    private val scope = CoroutineScope(Dispatchers.Main)
    private val random = Random()

    @Provides
    @Singleton
    fun provideDispatcherProvider(): DispatcherProvider = DefaultDispatcherProvider()

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
    fun provideHumanVerificationHandler() =
        HumanVerificationHandler(scope, ProtonApplication.getAppContext() as ProtonApplication)

    @Provides
    @Singleton
    fun provideProtonCookieStore(): ProtonCookieStore =
        ProtonCookieStore(ProtonApplication.getAppContext())

    @Provides
    @Singleton
    fun provideClientIdProvider(protonCookieStore: ProtonCookieStore): ClientIdProvider =
        ClientIdProviderImpl(PRIMARY_VPN_API_URL, protonCookieStore)

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
        val logger = CoreLogger()
        val sessionProvider = userData.apiSessionProvider
        return if (BuildConfig.DEBUG) {
            ApiManagerFactory(PRIMARY_VPN_API_URL, apiClient, clientIdProvider, logger, networkManager,
                NetworkPrefs(appContext), sessionProvider, sessionProvider, humanVerificationHandler,
                humanVerificationHandler, cookieStore, scope, certificatePins = emptyArray(),
                alternativeApiPins = emptyList())
        } else {
            ApiManagerFactory(PRIMARY_VPN_API_URL, apiClient, clientIdProvider, logger, networkManager,
                NetworkPrefs(appContext), sessionProvider, sessionProvider, humanVerificationHandler,
                humanVerificationHandler, cookieStore, scope)
        }
    }

    @Singleton
    @Provides
    fun provideApiProvider(apiFactory: ApiManagerFactory, userData: UserData): ApiProvider =
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
    fun provideUserPrefs(): UserData = UserData.load()

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
        certificateRepository: CertificateRepository, // Make sure that CertificateRepository instance is created
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
    fun provideCertificateRepository(
        userData: UserData,
        api: ProtonApiRetroFit,
        userPlanManager: UserPlanManager
    ): CertificateRepository = CertificateRepository(
        scope,
        ProtonApplication.getAppContext(),
        userData,
        api,
        System::currentTimeMillis,
        userPlanManager)

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
        serverManager: ServerManager,
        certificateRepository: CertificateRepository
    ): VpnBackendProvider =
        ProtonVpnBackendProvider(
            StrongSwanBackend(
                random,
                networkManager,
                scope,
                System::currentTimeMillis,
                userData,
                appConfig,
                certificateRepository
            ),
            OpenVpnBackend(
                random,
                networkManager,
                userData,
                appConfig,
                System::currentTimeMillis,
                certificateRepository,
                scope
            ),
            WireguardBackend(
                ProtonApplication.getAppContext(),
                GoBackend(ProtonApplication.getAppContext()),
                networkManager,
                userData,
                appConfig,
                certificateRepository,
                scope
            ),
            serverManager
        )

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
        vpnApiClient: VpnApiClient,
        humanVerificationHandler: HumanVerificationHandler,
        certificateRepository: CertificateRepository
    ): LogoutHandler = LogoutHandler(scope, userData, serverManager, vpnApiManager, userData.apiSessionProvider,
        vpnStateMonitor, vpnConnectionManager, humanVerificationHandler, certificateRepository, vpnApiClient)
}

@Module
@InstallIn(SingletonComponent::class)
object HiltAppModule {

    @Provides
    @Singleton
    fun provideCountriesRepository(): CountriesRepository =
        CountriesRepositoryImpl(ProtonApplication.getAppContext())

    @Provides
    @Singleton
    fun provideUserValidationRepository(
        apiProvider: ApiProvider,
        clientIdProvider: ClientIdProvider,
        humanVerificationHandler: HumanVerificationHandler
    ): UserVerificationRepository =
        UserVerificationRepositoryImpl(apiProvider, clientIdProvider, humanVerificationHandler)

    @Provides
    @Singleton
    fun provideHumanVerificationWorkflowHandler(
        humanVerificationHandler: HumanVerificationHandler
    ): HumanVerificationWorkflowHandler = humanVerificationHandler

    @Provides
    @CaptchaBaseUrl
    fun provideCaptchaBaseUrl(): String = BuildConfig.API_DOMAIN.removeSuffix("/api")
}
