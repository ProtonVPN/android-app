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
package com.protonvpn.android.ui.login

import android.os.Bundle
import com.protonvpn.android.components.BaseActivityV2
import com.protonvpn.android.databinding.ActivityAssignVpnConnectionBinding
import com.protonvpn.android.utils.Constants.URL_SUPPORT_ASSIGN_VPN_CONNECTION
import com.protonvpn.android.utils.openUrl
import dagger.hilt.android.AndroidEntryPoint
import me.proton.core.presentation.utils.onClick
import me.proton.core.presentation.utils.viewBinding

@AndroidEntryPoint
class AssignVpnConnectionActivity : BaseActivityV2() {

    private val binding by viewBinding(ActivityAssignVpnConnectionBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        with(binding) {
            buttonAssignVpnConnections.onClick {
                openUrl(URL_SUPPORT_ASSIGN_VPN_CONNECTION)
            }
            buttonReturnToLogin.onClick {
                finish()
            }
        }
    }
}
