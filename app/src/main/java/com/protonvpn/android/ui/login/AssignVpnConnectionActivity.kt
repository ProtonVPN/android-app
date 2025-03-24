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
package com.protonvpn.android.ui.login

import android.os.Bundle
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseActivityV2
import com.protonvpn.android.databinding.ActivityAssignVpnConnectionBinding
import com.protonvpn.android.utils.Constants.URL_SUPPORT_ASSIGN_VPN_CONNECTION
import com.protonvpn.android.utils.openUrl
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import me.proton.core.presentation.utils.onClick
import me.proton.core.presentation.utils.viewBinding
import me.proton.core.user.domain.entity.Role

@AndroidEntryPoint
class AssignVpnConnectionActivity : BaseActivityV2() {

    private val binding by viewBinding(ActivityAssignVpnConnectionBinding::inflate)
    private val viewModel by viewModels<AssignVpnConnectionViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        with(binding) {
            lifecycleScope.launch {
                val role = viewModel.getUserRole()
                val isAdmin = role == Role.OrganizationAdmin
                buttonAssignVpnConnections.isVisible = isAdmin
                textDescriptionAssignConnections.text =
                    getString(if (isAdmin) R.string.connectionAllocationHelpDescription1 else R.string.connectionAllocationHelpDescription1AskAdmin)
                textSubDescriptionAssignConnections.isVisible = isAdmin

            }
            buttonAssignVpnConnections.onClick {
                openUrl(URL_SUPPORT_ASSIGN_VPN_CONNECTION)
            }
            buttonReturnToLogin.onClick {
                finish()
            }
        }
    }
}
