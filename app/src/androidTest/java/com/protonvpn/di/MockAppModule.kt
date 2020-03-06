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

import android.os.SystemClock
import com.google.gson.Gson
import com.protonvpn.android.BuildConfig
import com.protonvpn.android.ProtonApplication
import com.protonvpn.android.api.AlternativeApiManager
import com.protonvpn.android.api.ProtonApiManager
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.api.ProtonPrimaryApiBackend
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.utils.TrafficMonitor
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.mocks.MockVpnBackendProvider
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Main
import java.util.*
import javax.inject.Singleton

@Module
class MockAppModule {

    private val scope = CoroutineScope(Main)
    private val random = Random()

    @Singleton
    @Provides
    fun provideServerManager(userData: UserData?) =
        ServerManager(ProtonApplication.getAppContext(), userData)

    @Singleton
    @Provides
    fun provideServerListUpdater(
        api: ProtonApiRetroFit,
        serverManager: ServerManager,
        userData: UserData
    ) = ServerListUpdater(scope, api, serverManager, userData)

    @Singleton
    @Provides
    fun provideProtonApiManager(userData: UserData): ProtonApiManager {
        val altApiManager = object : AlternativeApiManager(
            BuildConfig.API_DOMAIN,
            userData,
            System::currentTimeMillis
        ) {
            override fun createAltBackend(baseUrl: String) = throw NotImplementedError()
            override fun getDnsOverHttpsProviders() = emptyArray<DnsOverHttpsProvider>()
        }
        val primaryApiBackend = ProtonPrimaryApiBackend(Constants.PRIMARY_VPN_API_URL)
        return ProtonApiManager(ProtonApplication.getAppContext(),
                userData, altApiManager, primaryApiBackend, random)
    }

    @Singleton
    @Provides
    fun provideAPI(apiManager: ProtonApiManager): ProtonApiRetroFit = MockApi(scope, apiManager)

    @Singleton
    @Provides
    fun provideGson() = Gson()

    @Singleton
    @Provides
    fun provideUserPrefs(): UserData = Storage.load(UserData::class.java, UserData())

    @Singleton
    @Provides
    fun provideVpnStateMonitor(
        userData: UserData,
        api: ProtonApiRetroFit,
        backendManager: MockVpnBackendProvider,
        serverListUpdater: ServerListUpdater,
        trafficMonitor: TrafficMonitor,
        apiManager: ProtonApiManager
    ): VpnStateMonitor = MockVpnStateMonitor(userData, api, backendManager, serverListUpdater,
                trafficMonitor, apiManager, scope)

    @Singleton
    @Provides
    fun provideVpnBackendManager() = MockVpnBackendProvider()

    @Singleton
    @Provides
    fun provideTrafficMonitor() = TrafficMonitor(
            ProtonApplication.getAppContext(), scope, SystemClock::elapsedRealtime)
}
