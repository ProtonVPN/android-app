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

import androidx.test.espresso.Espresso
import androidx.test.espresso.IdlingResource
import com.protonvpn.MockSwitch
import com.protonvpn.android.api.*
import com.protonvpn.android.components.LoaderUI
import com.protonvpn.android.models.login.GenericResponse
import com.protonvpn.android.models.login.SessionListResponse
import com.protonvpn.android.models.vpn.ServerList
import com.protonvpn.testsHelper.IdlingResourceHelper
import com.protonvpn.testsHelper.MockedServers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import retrofit2.Response

class MockApi(scope: CoroutineScope, manager: ProtonApiManager) : ProtonApiRetroFit(scope, manager) {

    override suspend fun getSession(): ApiResult<SessionListResponse> =
        ApiResult.Success(Response.success(SessionListResponse(0, listOf())))

    override fun logout(callback: NetworkResultCallback<GenericResponse>) = scope.launch {
        callback.onSuccess(GenericResponse(1000))
    }

    override suspend fun getServerList(loader: LoaderUI?, ip: String?) =
        if (MockSwitch.mockedServersUsed)
            ApiResult.Success(Response.success(ServerList(MockedServers.getServerList())))
        else
            super.getServerList(loader, ip)

    init {
        val resource: IdlingResource = IdlingResourceHelper.create("OkHttp", getOkClient())
        Espresso.registerIdlingResources(resource)
    }
}
