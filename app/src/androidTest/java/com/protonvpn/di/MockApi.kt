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

import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.api.ProtonVPNRetrofit
import com.protonvpn.android.api.VpnApiManager
import com.protonvpn.android.appconfig.ApiNotificationsResponse
import com.protonvpn.android.appconfig.ForkedSessionResponse
import com.protonvpn.android.appconfig.AppConfigResponse
import com.protonvpn.android.appconfig.DefaultPortsConfig
import com.protonvpn.android.appconfig.FeatureFlags
import com.protonvpn.android.appconfig.RatingConfig
import com.protonvpn.android.appconfig.SessionForkSelectorResponse
import com.protonvpn.android.appconfig.SmartProtocolConfig
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.components.LoaderUI
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.login.GenericResponse
import com.protonvpn.android.models.login.SessionListResponse
import com.protonvpn.android.models.vpn.CertificateResponse
import com.protonvpn.android.models.vpn.ServerList
import com.protonvpn.android.models.vpn.StreamingServicesResponse
import com.protonvpn.android.models.vpn.UserLocation
import com.protonvpn.test.shared.ApiNotificationTestHelper
import com.protonvpn.test.shared.MockedServers
import com.protonvpn.test.shared.TestUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import me.proton.core.domain.arch.DataResult
import me.proton.core.domain.arch.ResponseSource
import me.proton.core.domain.entity.SessionUserId
import me.proton.core.network.data.ApiProvider
import me.proton.core.network.domain.ApiResult
import me.proton.core.network.domain.NetworkStatus
import me.proton.core.network.domain.session.SessionId
import me.proton.core.user.data.repository.UserRepositoryImpl
import me.proton.core.user.domain.entity.User
import me.proton.core.user.domain.repository.UserRepository
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

class MockApi(
    scope: CoroutineScope,
    apiProvider: ApiProvider,
    val userData: UserData,
    val currentUser: CurrentUser,
) : ProtonApiRetroFit(scope, MockNetworkApiManager(apiProvider, currentUser), null) {

    private class MockNetworkApiManager(
        apiProvider: ApiProvider,
        currentUser: CurrentUser,
    ) : VpnApiManager(apiProvider, currentUser) {

        override suspend fun <T> invoke(
            forceNoRetryOnConnectionErrors: Boolean,
            block: suspend ProtonVPNRetrofit.() -> T
        ): ApiResult<T> =
            if (MockNetworkManager.currentStatus == NetworkStatus.Disconnected) {
                ApiResult.Error.NoInternet()
            } else {
                super.invoke(forceNoRetryOnConnectionErrors, block)
            }

        override suspend fun <T> invoke(
            sessionId: SessionId?,
            forceNoRetryOnConnectionErrors: Boolean,
            block: suspend ProtonVPNRetrofit.() -> T
        ): ApiResult<T> =
            if (MockNetworkManager.currentStatus == NetworkStatus.Disconnected) {
                ApiResult.Error.NoInternet()
            } else {
                super.invoke(sessionId, forceNoRetryOnConnectionErrors, block)
            }
    }

    var forkedUserResponse: ApiResult<ForkedSessionResponse> =
        ApiResult.Success(TestUser.forkedSessionResponse)

    override suspend fun getApiNotifications(
        supportedFormats: List<String>,
        fullScreenImageWidthPx: Int,
        fullScreenImageHeightPx: Int
    ): ApiResult<ApiNotificationsResponse> =
        // Empty response. Notifications tests use ApiNotificationsManager.setTestNotificationsResponseJson to test
        // different notification types.
        ApiResult.Success(ApiNotificationTestHelper.mockResponse())

    override suspend fun getAppConfig(netzone: String?): ApiResult<AppConfigResponse> =
        ApiResult.Success(AppConfigResponse(
            featureFlags = FeatureFlags(
                maintenanceTrackerEnabled = true,
                netShieldEnabled = true,
                pollApiNotifications = true,
                vpnAccelerator = true),
            smartProtocolConfig = SmartProtocolConfig(
                ikeV2Enabled = true,
                openVPNEnabled = true,
                wireguardEnabled = false,
                wireguardTcpEnabled = false,
                wireguardTlsEnabled = false
            ),
            defaultPortsConfig = DefaultPortsConfig.defaultConfig,
            ratingConfig = RatingConfig(listOf("vpnplus"), 3, 14, 3, 14)
        ))

    override suspend fun getSession(): ApiResult<SessionListResponse> =
        ApiResult.Success(SessionListResponse(0, listOf()))

    override suspend fun logout() =
        ApiResult.Success(GenericResponse(1000))

    override suspend fun getServerList(loader: LoaderUI?, ip: String?, lang: String) =
        ApiResult.Success(ServerList(MockedServers.serverList))

    override suspend fun getSessionForkSelector(): ApiResult<SessionForkSelectorResponse> =
        ApiResult.Success(SessionForkSelectorResponse("dummy value", "1234ABCD"))

    override suspend fun getForkedSession(selector: String): ApiResult<ForkedSessionResponse> =
        forkedUserResponse

    override suspend fun getCertificate(sessionId: SessionId, clientPublicKey: String): ApiResult<CertificateResponse> {
        val now = System.currentTimeMillis()
        return ApiResult.Success(
            CertificateResponse(
                TEST_CERT,
                now + TimeUnit.DAYS.toMillis(1),
                now + TimeUnit.HOURS.toMillis(16)
            )
        )
    }

    override suspend fun getLocation() = ApiResult.Success(UserLocation("127.0.0.1", "US", ""))

    override suspend fun getStreamingServices() = ApiResult.Success(
        StreamingServicesResponse("https://protonvpn.com/download/resources/", emptyMap()))

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

@Singleton
class MockUserRepository @Inject constructor(
    private val userRepositoryImpl: UserRepositoryImpl
) : UserRepository by userRepositoryImpl {

    private val useMockUser = MutableStateFlow(false)
    private val mockUser = MutableSharedFlow<User>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    suspend fun setMockUser(user: User) {
        useMockUser.value = true
        mockUser.emit(user)
    }

    override suspend fun getUser(sessionUserId: SessionUserId, refresh: Boolean) =
        if (useMockUser.value) mockUser.first() else userRepositoryImpl.getUser(sessionUserId, refresh)

    override fun getUserFlow(sessionUserId: SessionUserId, refresh: Boolean): Flow<DataResult<User>> =
        useMockUser.flatMapLatest { useMockUser ->
            if (useMockUser) mockUser.map {
                DataResult.Success(ResponseSource.Local, it)
            } else {
                userRepositoryImpl.getUserFlow(sessionUserId, refresh)
            }
        }
}
