/*
 * Copyright (c) 2020 Proton Technologies AG
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
package com.protonvpn.android.components

import androidx.appcompat.app.AppCompatActivity
import com.protonvpn.android.tv.vpn.VpnUiActivityDelegateTv
import com.protonvpn.android.ui.vpn.VpnUiActivityDelegate

abstract class BaseTvActivity : AppCompatActivity(), VpnUiDelegateProvider {

    @Suppress("LeakingThis")
    private val vpnUiDelegate = VpnUiActivityDelegateTv(this)

    override fun getVpnUiDelegate(): VpnUiActivityDelegate = vpnUiDelegate
}
