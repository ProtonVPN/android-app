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
import com.protonvpn.android.components.LoaderUI
import com.protonvpn.android.models.login.GenericResponse
import com.protonvpn.android.models.login.SessionListResponse
import com.protonvpn.android.models.login.VpnInfoResponse
import com.protonvpn.android.models.vpn.ServerList
import com.protonvpn.testsHelper.MockedServers
import com.protonvpn.testsHelper.TestUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.proton.core.network.domain.ApiManager
import me.proton.core.network.domain.ApiResult

class MockApi(scope: CoroutineScope, manager: ApiManager<ProtonVPNRetrofit>) : ProtonApiRetroFit(scope, manager) {

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
        ApiResult.Success(TestUser.getBasicUser().vpnInfoResponse)

    override fun getVPNInfo(callback: NetworkResultCallback<VpnInfoResponse>) = scope.launch {
        ApiResult.Success(TestUser.getBasicUser().vpnInfoResponse)
    }
}
