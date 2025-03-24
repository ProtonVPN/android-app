/*
 * Copyright (c) 2020 Proton AG
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
package com.protonvpn.android.api

import com.protonvpn.android.auth.usecase.CurrentUser
import me.proton.core.network.data.ApiProvider
import me.proton.core.network.domain.ApiManager
import me.proton.core.network.domain.ApiResult
import me.proton.core.network.domain.session.SessionId

// ApiManager instance representing current session (or unauthorized session at all when logged out)
open class VpnApiManager(
    private val apiProvider: ApiProvider,
    private val currentUser: CurrentUser,
) : ApiManager<ProtonVPNRetrofit> {

    // ApiProvider holds only weak references of ApiManagers, cache last ApiManager to avoid risk of creating new
    // ApiManager each API call - as reference to it will not be kept anywhere, only to VpnApiManager (this)
    private var cachedManager: ApiManager<*>? = null

    override suspend operator fun <T> invoke(
        forceNoRetryOnConnectionErrors: Boolean,
        block: suspend ProtonVPNRetrofit.() -> T
    ): ApiResult<T> =
        invoke(currentUser.sessionId(), forceNoRetryOnConnectionErrors, block)

    open suspend operator fun <T> invoke(
        sessionId: SessionId? = null,
        forceNoRetryOnConnectionErrors: Boolean = false,
        block: suspend ProtonVPNRetrofit.() -> T
    ): ApiResult<T> = apiProvider.get<ProtonVPNRetrofit>(sessionId ?: currentUser.sessionId()).apply {
        cachedManager = this
    }.invoke(forceNoRetryOnConnectionErrors, block)
}
