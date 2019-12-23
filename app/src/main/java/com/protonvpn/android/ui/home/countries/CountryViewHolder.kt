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
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.animation.AnimationUtils
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.protonvpn.android.R
import com.protonvpn.android.databinding.ItemCountryBinding
import com.protonvpn.android.models.vpn.VpnCountry
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.vpn.VpnStateMonitor
import com.xwray.groupie.ExpandableGroup
import com.xwray.groupie.ExpandableItem
import com.xwray.groupie.databinding.BindableItem
import com.xwray.groupie.databinding.GroupieViewHolder

abstract class CountryViewHolder(private val viewModel: CountryListViewModel, private val vpnCountry: VpnCountry, val parentLifecycleOwner: LifecycleOwner) :
        BindableItem<ItemCountryBinding>(), ExpandableItem {

    private lateinit var expandableGroup: ExpandableGroup
    private lateinit var binding: ItemCountryBinding
    private val countrySelectionObserver = Observer<VpnCountry> {
        updateUpgradeButton(animate = true)
    }

    abstract fun onExpanded(position: Int)

    private val vpnStateObserver = Observer<VpnStateMonitor.VpnState> {
        binding.textConnected.visibility =
                if (vpnCountry.hasConnectedServer(it.server) && it.state == VpnStateMonitor.State.CONNECTED) VISIBLE else GONE
    }

    override fun getId() = vpnCountry.flag.hashCode().toLong()

    override fun bind(viewBinding: ItemCountryBinding, position: Int) {
        // Sometimes we can get 2 binds in a row without unbind in between
        clearObservers()
        val context = viewBinding.root.context
        binding = viewBinding
        with(viewBinding) {
            textCountry.setTextColor(ContextCompat.getColor(context,
                    if (vpnCountry.hasAccessibleServer(viewModel.userData)) R.color.white else R.color.white50))
            textCountry.text = if (vpnCountry.hasAccessibleServer(viewModel.userData))
                vpnCountry.countryName
            else
                vpnCountry.countryName + " " + context.getString(if (vpnCountry.isUnderMaintenance()) R.string.listItemMaintenance else R.string.premium)

            buttonCross.visibility =
                    if (vpnCountry.hasAccessibleServer(viewModel.userData)) VISIBLE else GONE

            adjustCross(buttonCross, vpnCountry.isExpanded(), 0)
            imageCountry.setImageResource(
                    CountryTools.getFlagResource(context, vpnCountry.flag))
            viewModel.vpnStateMonitor.vpnState.observe(parentLifecycleOwner, vpnStateObserver)

            imageDoubleArrows.visibility =
                    if (viewModel.userData.isSecureCoreEnabled) VISIBLE else GONE
            badgeP2P.visibility =
                    if (vpnCountry.getKeywords().contains("p2p")) VISIBLE else GONE
            badgeTor.visibility =
                    if (vpnCountry.getKeywords().contains("tor")) VISIBLE else GONE

            root.setOnClickListener {
                if (vpnCountry.hasAccessibleServer(viewModel.userData)) {
                    expandableGroup.onToggleExpanded()
                    if (expandableGroup.isExpanded) {
                        onExpanded(position)
                    }
                    adjustCross(buttonCross, expandableGroup.isExpanded, 300)
                } else {
                    clickedOnUpgradeCountry()
                }
            }

            buttonUpgrade.setOnClickListener { viewModel.onUpgradeTriggered.emit() }
            buttonUpgrade.isVisible = false
            buttonUpgrade.clearAnimation()
            updateUpgradeButton(animate = false)
            viewModel.selectedCountry.observe(parentLifecycleOwner, countrySelectionObserver)
        }
    }

    private fun clickedOnUpgradeCountry() {
        viewModel.selectedCountry.value =
                if (viewModel.selectedCountry.value == vpnCountry) null else vpnCountry
    }

    private fun updateUpgradeButton(animate: Boolean) {
        val show = viewModel.selectedCountry.value?.flag == vpnCountry.flag
        with(binding.buttonUpgrade) {
            if (show && !isVisible) {
                val anim = AnimationUtils.loadAnimation(context, R.anim.slide_in_from_right)
                anim.duration = if (animate) 300 else 0
                startAnimation(anim)
            }
            isVisible = show
        }
    }

    override fun unbind(viewHolder: GroupieViewHolder<ItemCountryBinding>) {
        super.unbind(viewHolder)
        clearObservers()
    }

    private fun clearObservers() {
        viewModel.selectedCountry.removeObserver(countrySelectionObserver)
        viewModel.vpnStateMonitor.vpnState.removeObserver(vpnStateObserver)
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
