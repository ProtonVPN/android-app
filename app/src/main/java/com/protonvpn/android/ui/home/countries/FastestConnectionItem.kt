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

package com.protonvpn.android.ui.home.countries

import android.view.View
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.protonvpn.android.R
import com.protonvpn.android.components.VpnUiDelegateProvider
import com.protonvpn.android.databinding.ItemFastestConnectionBinding
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.base.ui.Flag
import com.protonvpn.android.utils.BindableItemEx
import com.protonvpn.android.utils.getActivity
import com.protonvpn.android.vpn.VpnStateMonitor

class FastestConnectionItem(
    private val viewModel: CountryListViewModel,
    private val parentLifeCycle: LifecycleOwner,
    private val item: FastestConnectionModel
): BindableItemEx<ItemFastestConnectionBinding>() {

    private val vpnStateObserver = Observer<VpnStateMonitor.Status>{
        binding.buttonConnect.isOn = viewModel.isConnectedTo(item.connectIntent)
    }

    override fun bind(viewBinding: ItemFastestConnectionBinding, position: Int) {
        super.bind(viewBinding, position)
        with(viewBinding) {
            composeViewFlag.setContent {
                val secureCore = viewModel.secureCore.collectAsStateWithLifecycle(initialValue = false).value
                Flag(
                    exitCountry = CountryId.fastest,
                    entryCountry = CountryId.fastest.takeIf { secureCore },
                )
            }
            textLabel.setText(item.name)
            val vpnUiDelegate = (root.context.getActivity() as VpnUiDelegateProvider).getVpnUiDelegate()
            buttonConnect.setOnClickListener {
                viewModel.connectOrDisconnect(vpnUiDelegate, item.connectIntent, "fastest in country list")
            }
        }
        viewModel.vpnStatus.observe(parentLifeCycle, vpnStateObserver)
    }

    override fun clear() {
        viewModel.vpnStatus.removeObserver(vpnStateObserver)
    }

    override fun getLayout(): Int = R.layout.item_fastest_connection

    override fun initializeViewBinding(view: View) = ItemFastestConnectionBinding.bind(view)
}
