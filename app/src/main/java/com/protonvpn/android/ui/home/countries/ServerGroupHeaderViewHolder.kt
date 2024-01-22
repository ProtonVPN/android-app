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
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.protonvpn.android.R
import com.protonvpn.android.databinding.ItemVpnCountryBinding
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.base.ui.Flag
import com.protonvpn.android.ui.planupgrade.UpgradeCountryHighlightsFragment
import com.protonvpn.android.ui.planupgrade.UpgradeDialogActivity
import com.protonvpn.android.utils.AndroidUtils.getFloatRes
import com.protonvpn.android.utils.BindableItemEx
import com.protonvpn.android.utils.getSelectableItemBackgroundRes
import com.protonvpn.android.utils.getThemeColor
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.xwray.groupie.ExpandableGroup
import com.xwray.groupie.ExpandableItem
import java.util.Objects
import me.proton.core.presentation.R as CoreR

private const val EXPAND_DURATION_MS = 300L

abstract class ServerGroupHeaderViewHolder(
    private val viewModel: CountryListViewModel,
    private val serverGroupModel: CollapsibleServerGroupModel,
    private val parentLifecycleOwner: LifecycleOwner
) : BindableItemEx<ItemVpnCountryBinding>(), ExpandableItem {

    private lateinit var expandableGroup: ExpandableGroup

    abstract fun onExpanded(position: Int)

    private val vpnStateObserver = Observer<VpnStateMonitor.Status> {
        binding.textConnected.isVisible =
            serverGroupModel.haveServer(it.server) && it.state == VpnState.Connected && serverGroupModel.accessible
    }

    override fun getId() = Objects.hash(serverGroupModel.id).toLong()

    override fun bind(viewBinding: ItemVpnCountryBinding, position: Int) {
        super.bind(viewBinding, position)

        val context = viewBinding.root.context
        with(viewBinding) {
            val isAccessibleAndOnline = serverGroupModel.accessible && serverGroupModel.online
            textCountry.text = serverGroupModel.title
            features.featureIcons = serverGroupModel.featureIcons()
            countryItem.setBackgroundResource(
                if (isAccessibleAndOnline) countryItem.getSelectableItemBackgroundRes() else 0
            )
            textCountry.setTextColor(
                textCountry.getThemeColor(
                    if (isAccessibleAndOnline) CoreR.attr.proton_text_norm else CoreR.attr.proton_text_hint
                )
            )
            buttonCross.isVisible = isAccessibleAndOnline
            adjustCross(buttonCross, expandableGroup.isExpanded, 0)
            adjustDivider(divider, expandableGroup.isExpanded, 0)
            composeViewFlag.setContent {
                if (serverGroupModel.isGatewayGroup()) {
                    Image(
                        painterResource(id = R.drawable.ic_gateway_flag),
                        contentDescription = null,
                    )
                } else {
                    serverGroupModel.countryFlag?.let {
                        Flag(
                            exitCountry = CountryId(it),
                            entryCountry = CountryId.fastest.takeIf { serverGroupModel.secureCore },
                        )
                    }
                }
            }
            composeViewFlag.alpha =
                if (isAccessibleAndOnline) 1f else root.resources.getFloatRes(R.dimen.inactive_flag_alpha)
            features.color = context.getColor(
                if (!isAccessibleAndOnline) CoreR.color.icon_weak else CoreR.color.icon_norm
            )

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
            iconUnderMaintenance.isVisible = !serverGroupModel.online && serverGroupModel.accessible
            buttonUpgrade.isVisible = !serverGroupModel.accessible
            buttonUpgrade.setOnClickListener {
                val flag = serverGroupModel.countryFlag
                if (flag != null) {
                    UpgradeDialogActivity.launch<UpgradeCountryHighlightsFragment>(
                        it.context,
                        UpgradeCountryHighlightsFragment.args(flag)
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
