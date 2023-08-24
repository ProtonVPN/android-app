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

import android.content.Context
import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.protonvpn.android.R
import com.protonvpn.android.components.featureIcons
import com.protonvpn.android.databinding.ItemVpnCountryBinding
import com.protonvpn.android.models.vpn.GatewayGroup
import com.protonvpn.android.models.vpn.ServerGroup
import com.protonvpn.android.models.vpn.VpnCountry
import com.protonvpn.android.ui.planupgrade.UpgradeCountryDialogActivity
import com.protonvpn.android.utils.AndroidUtils.getFloatRes
import com.protonvpn.android.utils.BindableItemEx
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.utils.getSelectableItemBackgroundRes
import com.protonvpn.android.utils.getThemeColor
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.xwray.groupie.ExpandableGroup
import com.xwray.groupie.ExpandableItem
import java.util.Objects

private const val EXPAND_DURATION_MS = 300L

abstract class CountryViewHolder(
    private val viewModel: CountryListViewModel,
    private val group: ServerGroup,
    private val sectionId: String,
    private val isAccessibleAndOnline: Boolean,
    private val parentLifecycleOwner: LifecycleOwner
) : BindableItemEx<ItemVpnCountryBinding>(), ExpandableItem {

    private lateinit var expandableGroup: ExpandableGroup

    abstract fun onExpanded(position: Int)

    private val vpnStateObserver = Observer<VpnStateMonitor.Status> {
        binding.textConnected.isVisible =
            group.hasConnectedServer(it.server) && it.state == VpnState.Connected && isAccessibleAndOnline
    }

    override fun getId() = Objects.hash(group.id(), sectionId).toLong()

    override fun bind(viewBinding: ItemVpnCountryBinding, position: Int) {
        super.bind(viewBinding, position)

        val context = viewBinding.root.context
        with(viewBinding) {
            textCountry.text = group.name()
            imageCountry.setImageResource(group.iconResource(context))
            imageDoubleArrows.isVisible = viewModel.isSecureCoreEnabled
            features.featureIcons = group.featureIcons()
            countryItem.setBackgroundResource(
                if (isAccessibleAndOnline)
                    countryItem.getSelectableItemBackgroundRes() else 0
            )
            textCountry.setTextColor(
                textCountry.getThemeColor(
                    if (isAccessibleAndOnline) R.attr.proton_text_norm else R.attr.proton_text_hint
                )
            )
            buttonCross.isVisible = isAccessibleAndOnline
            imageCountry.alpha =
                if (isAccessibleAndOnline) 1f else root.resources.getFloatRes(R.dimen.inactive_flag_alpha)
            if (!isAccessibleAndOnline) {
                features.color = context.getColor(R.color.icon_weak)
            }
            root.setOnClickListener {
                if (isAccessibleAndOnline) {
                    expandableGroup.onToggleExpanded()
                    if (expandableGroup.isExpanded) {
                        onExpanded(position)
                    }
                    adjustCross(buttonCross, expandableGroup.isExpanded, EXPAND_DURATION_MS)
                    adjustDivider(divider, expandableGroup.isExpanded, EXPAND_DURATION_MS)
                }
            }
            iconUnderMaintenance.isVisible = group.isUnderMaintenance() && !isAccessibleAndOnline
            buttonUpgrade.isVisible = !isAccessibleAndOnline
            buttonUpgrade.setOnClickListener {
                if (group is VpnCountry) {
                    it.context.startActivity(
                        UpgradeCountryDialogActivity.createIntent(it.context, group.flag)
                    )
                }
            }
            viewModel.vpnStatus.observe(parentLifecycleOwner, vpnStateObserver)
        }
    }

    override fun clear() {
        viewModel.vpnStatus.removeObserver(vpnStateObserver)
    }

    override fun getLayout(): Int = R.layout.item_vpn_country

    override fun initializeViewBinding(view: View) = ItemVpnCountryBinding.bind(view)

    private fun adjustCross(view: View, expanded: Boolean, animDuration: Long) {
        view.animate().setDuration(animDuration).rotation((if (expanded) 0 else 180).toFloat())
            .start()
    }

    private fun adjustDivider(view: View, expanded: Boolean, animDurationMs: Long) {
        view.animate().setDuration(animDurationMs).alpha(if (expanded) 0f else 1f)
    }

    override fun setExpandableGroup(onToggleListener: ExpandableGroup) {
        this.expandableGroup = onToggleListener
    }
}

fun ServerGroup.iconResource(context: Context) = when (this) {
    is VpnCountry -> CountryTools.getFlagResource(context, flag)
    is GatewayGroup -> R.drawable.ic_proton_servers
}
