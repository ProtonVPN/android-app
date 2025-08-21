/*
 * Copyright (c) 2025. Proton AG
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

package com.protonvpn.android.auth

import me.proton.core.network.domain.ApiResult

private const val ERROR_CODE_NO_CONNECTIONS_ASSIGNED = 86_300
const val LOGIN_GUEST_HOLE_ID = "LOGIN_SIGNUP"

fun ApiResult<*>.isErrorNoConnectionsAssigned(): Boolean =
    (this as? ApiResult.Error.Http)?.proton?.code == ERROR_CODE_NO_CONNECTIONS_ASSIGNED
