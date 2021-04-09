/*
 * Copyright (c) 2019 Proton Technologies AG
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
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.protonvpn.android.R
import com.protonvpn.android.databinding.ItemCountryBinding
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.models.vpn.VpnCountry
import com.protonvpn.android.utils.BindableItemEx
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.utils.getSelectableItemBackgroundRes
import com.protonvpn.android.utils.openProtonUrl
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.xwray.groupie.ExpandableGroup
import com.xwray.groupie.ExpandableItem

abstract class CountryViewHolder(
    private val viewModel: CountryListViewModel,
    private val vpnCountry: VpnCountry,
    val parentLifecycleOwner: LifecycleOwner
) : BindableItemEx<ItemCountryBinding>(), ExpandableItem {

    private lateinit var expandableGroup: ExpandableGroup

    abstract fun onExpanded(position: Int)

    private val vpnStateObserver = Observer<VpnStateMonitor.Status> {
        binding.textConnected.isVisible =
                vpnCountry.hasConnectedServer(it.server) && it.state == VpnState.Connected
    }

    override fun getId() = vpnCountry.flag.hashCode().toLong()

    override fun bind(viewBinding: ItemCountryBinding, position: Int) {
        super.bind(viewBinding, position)

        val context = viewBinding.root.context
        with(viewBinding) {
            val accessible = vpnCountry.hasAccessibleOnlineServer(viewModel.userData)
            countryItem.setBackgroundResource(if (accessible)
                countryItem.getSelectableItemBackgroundRes() else 0)
            textCountry.setTextColor(ContextCompat.getColor(context,
                    if (vpnCountry.hasAccessibleOnlineServer(viewModel.userData)) R.color.white else R.color.white50))
            textCountry.text = if (accessible)
                vpnCountry.countryName
            else
                vpnCountry.countryName + " " + context.getString(if (vpnCountry.isUnderMaintenance()) R.string.listItemMaintenance else R.string.premium)

            buttonCross.isVisible = accessible

            adjustCross(buttonCross, expandableGroup.isExpanded, 0)
            imageCountry.setImageResource(
                    CountryTools.getFlagResource(context, vpnCountry.flag))
            viewModel.vpnStatus.observe(parentLifecycleOwner, vpnStateObserver)

            imageDoubleArrows.isVisible = viewModel.userData.isSecureCoreEnabled
            val keywords = vpnCountry.keywords
            iconP2P.isVisible = keywords.contains(Server.Keyword.P2P)
            iconTor.isVisible = keywords.contains(Server.Keyword.TOR)
            iconSmartRouting.isVisible = viewModel.shouldShowSmartRouting(vpnCountry)

            root.setOnClickListener {
                if (!vpnCountry.isUnderMaintenance()) {
                    if (accessible) {
                        expandableGroup.onToggleExpanded()
                        if (expandableGroup.isExpanded) {
                            onExpanded(position)
                        }
                        adjustCross(buttonCross, expandableGroup.isExpanded, 300)
                    }
                }
            }

            buttonUpgrade.isVisible = !vpnCountry.isUnderMaintenance() && !accessible
            buttonUpgrade.setOnClickListener {
                buttonUpgrade.context.openProtonUrl(Constants.DASHBOARD_URL)
            }
        }
    }

    override fun clear() {
        viewModel.vpnStatus.removeObserver(vpnStateObserver)
    }

    override fun getLayout(): Int {
        return R.layout.item_country
    }

    private fun adjustCross(view: View, expanded: Boolean, animDuration: Long) {
        view.animate().setDuration(animDuration).rotation((if (expanded) 0 else 180).toFloat()).start()
    }

    override fun setExpandableGroup(onToggleListener: ExpandableGroup) {
        this.expandableGroup = onToggleListener
    }
}
