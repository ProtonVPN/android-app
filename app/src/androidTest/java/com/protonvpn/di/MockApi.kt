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

import com.protonvpn.MockSwitch
import com.protonvpn.android.api.NetworkResultCallback
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.api.ProtonVPNRetrofit
import com.protonvpn.android.appconfig.ApiNotification
import com.protonvpn.android.appconfig.ApiNotificationTypes
import com.protonvpn.android.appconfig.ApiNotificationsResponse
import com.protonvpn.android.appconfig.ForkedSessionResponse
import com.protonvpn.android.appconfig.AppConfigResponse
import com.protonvpn.android.appconfig.DefaultPortsConfig
import com.protonvpn.android.appconfig.FeatureFlags
import com.protonvpn.android.appconfig.SmartProtocolConfig
import com.protonvpn.android.components.LoaderUI
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.login.GenericResponse
import com.protonvpn.android.models.login.SessionListResponse
import com.protonvpn.android.models.login.VpnInfoResponse
import com.protonvpn.android.models.vpn.CertificateResponse
import com.protonvpn.android.models.vpn.ServerList
import com.protonvpn.test.shared.ApiNotificationTestHelper
import com.protonvpn.test.shared.MockedServers
import com.protonvpn.test.shared.TestUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.proton.core.network.domain.ApiManager
import me.proton.core.network.domain.ApiResult
import java.util.concurrent.TimeUnit

class MockApi(scope: CoroutineScope, manager: ApiManager<ProtonVPNRetrofit>, val userData: UserData) : ProtonApiRetroFit(scope, manager) {

    override suspend fun getAppConfig(): ApiResult<AppConfigResponse> =
        ApiResult.Success(AppConfigResponse(
            featureFlags = FeatureFlags(
                maintenanceTrackerEnabled = true,
                netShieldEnabled = true,
                pollApiNotifications = true,
                vpnAccelerator = true),
            smartProtocolConfig = SmartProtocolConfig(
                ikeV2Enabled = true,
                openVPNEnabled = true,
                wireguardEnabled = false
            ),
            defaultPortsConfig = DefaultPortsConfig.defaultConfig
        ))

    override suspend fun getSession(): ApiResult<SessionListResponse> =
        ApiResult.Success(SessionListResponse(0, listOf()))

    override suspend fun logout() =
        ApiResult.Success(GenericResponse(1000))

    override suspend fun getServerList(loader: LoaderUI?, ip: String?) =
        if (MockSwitch.mockedServersUsed)
            ApiResult.Success(ServerList(MockedServers.serverList))
        else
            super.getServerList(loader, ip)

    override suspend fun getVPNInfo(): ApiResult<VpnInfoResponse> =
        ApiResult.Success(userData.vpnInfoResponse ?: TestUser.getBasicUser().vpnInfoResponse)

    override fun getVPNInfo(callback: NetworkResultCallback<VpnInfoResponse>) = scope.launch {
        ApiResult.Success(userData.vpnInfoResponse ?: TestUser.getBasicUser().vpnInfoResponse)
    }

    override suspend fun getForkedSession(selector: String): ApiResult<ForkedSessionResponse> =
            ApiResult.Success(TestUser.getForkedSessionResponse())

    override suspend fun getApiNotifications(): ApiResult<ApiNotificationsResponse> =
        ApiResult.Success(ApiNotificationTestHelper.mockResponse(
            ApiNotificationTestHelper.mockOffer("1", Long.MIN_VALUE, Long.MIN_VALUE + 1, PAST_OFFER_LABEL),
            ApiNotificationTestHelper.mockOffer("2", Long.MIN_VALUE, Long.MAX_VALUE, OFFER_LABEL),
            ApiNotificationTestHelper.mockOffer("3", Long.MAX_VALUE - 1, Long.MAX_VALUE, FUTURE_OFFER_LABEL),
            ApiNotification("2", Long.MIN_VALUE, Long.MAX_VALUE, ApiNotificationTypes.TYPE_OFFER + 1)
        ))

    override suspend fun getCertificate(clientPublicKey: String): ApiResult<CertificateResponse> =
        if (MockSwitch.mockedConnectionUsed) {
            val now = System.currentTimeMillis()
            ApiResult.Success(CertificateResponse(
                TEST_CERT,
                now + TimeUnit.DAYS.toMillis(1),
                now + TimeUnit.HOURS.toMillis(16)))
        } else
            super.getCertificate(clientPublicKey)

    companion object {
        const val OFFER_LABEL = "Offer"
        const val PAST_OFFER_LABEL = "Past"
        const val FUTURE_OFFER_LABEL = "Future"
        const val TEST_CERT =
            "-----BEGIN CERTIFICATE-----\n" +
            "MIIBHjCB0QIUTM7tBq1mnKSLlwuWugFy1uFiV/YwBQYDK2VwMDIxCzAJBgNVBAYT\n" +
            "AkNIMQ8wDQYDVQQIDAZHZW5ldmExEjAQBgNVBAoMCXRlc3QgY2VydDAeFw0yMTA0\n" +
            "MjgxNjMwMDBaFw0zMTA0MjYxNjMwMDBaMDIxCzAJBgNVBAYTAkNIMQ8wDQYDVQQI\n" +
            "DAZHZW5ldmExEjAQBgNVBAoMCXRlc3QgY2VydDAqMAUGAytlcAMhAIEQpBEp1Hxl\n" +
            "N7IX/oeN5oIRfNjRNtCqcRLZ0iKdfUuUMAUGAytlcANBAGjIXvothfBryJqC6X3L\n" +
            "Gc6wQfhBE6PxkcJLLguvNvIAK197SATYz+KJfjyOlWnuy9El0v/DBCQ3Y44oaZTN\n" +
            "kQE=\n" +
            "-----END CERTIFICATE-----"
    }
}
