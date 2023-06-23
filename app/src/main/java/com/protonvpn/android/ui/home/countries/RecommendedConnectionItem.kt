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
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.protonvpn.android.R
import com.protonvpn.android.components.VpnUiDelegateProvider
import com.protonvpn.android.databinding.ItemRecommendedConnectionBinding
import com.protonvpn.android.utils.BindableItemEx
import com.protonvpn.android.utils.getActivity
import com.protonvpn.android.vpn.VpnStateMonitor

class RecommendedConnectionItem(
    private val viewModel: CountryListViewModel,
    private val parentLifeCycle: LifecycleOwner,
    private val item: RecommendedConnectionModel
): BindableItemEx<ItemRecommendedConnectionBinding>() {

    private val vpnStateObserver = Observer<VpnStateMonitor.Status>{
        binding.buttonConnect.isOn = viewModel.isConnectedTo(item.connectIntent)
    }

    override fun bind(viewBinding: ItemRecommendedConnectionBinding, position: Int) {
        super.bind(viewBinding, position)
        with(viewBinding) {
            imageIcon.setImageResource(item.icon)
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

    override fun getLayout(): Int = R.layout.item_recommended_connection

    override fun initializeViewBinding(view: View) = ItemRecommendedConnectionBinding.bind(view)
}
