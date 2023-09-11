/*
 * Copyright (c) 2023. Proton AG
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

package com.protonvpn.android.ui.home.vpn

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.protonvpn.android.base.ui.ComposeBottomSheetDialogFragment
import com.protonvpn.android.components.VpnUiDelegateProvider
import com.protonvpn.android.ui.planupgrade.EmptyUpgradeDialogActivity
import com.protonvpn.android.utils.AndroidUtils.launchActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ChangeServerBottomSheet : ComposeBottomSheetDialogFragment() {

    // Use the parent's ViewModel to get its state immediately and avoid defaults from being shown for a split second.
    private val viewModel: ChangeServerViewModel by viewModels({ requireParentFragment() })

    @Composable
    override fun Content() {
        val state by viewModel.state.collectAsStateWithLifecycle()
        UpgradeModalContent(
            state,
            onChangeServerClick = {
                viewModel.changeServer((requireActivity() as VpnUiDelegateProvider).getVpnUiDelegate())
            },
            onUpgradeClick = { requireContext().launchActivity<EmptyUpgradeDialogActivity>() }
        )
    }
}
