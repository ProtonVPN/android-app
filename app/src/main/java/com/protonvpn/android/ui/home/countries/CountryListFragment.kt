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

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.protonvpn.android.R
import com.protonvpn.android.databinding.FragmentCountryListBinding
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.logging.Setting
import com.protonvpn.android.logging.logUiSettingChange
import com.protonvpn.android.telemetry.UpgradeSource
import com.protonvpn.android.telemetry.UpgradeTelemetry
import com.protonvpn.android.ui.HeaderViewHolder
import com.protonvpn.android.ui.home.SecureCoreSpeedInfoActivity.Companion.createContract
import com.protonvpn.android.ui.planupgrade.UpgradeDialogActivity
import com.protonvpn.android.ui.planupgrade.UpgradeFlowType
import com.protonvpn.android.ui.planupgrade.UpgradePlusCountriesHighlightsFragment
import com.protonvpn.android.ui.planupgrade.UpgradeSecureCoreHighlightsFragment
import com.protonvpn.android.ui.promooffers.PromoOfferButtonActions
import com.protonvpn.android.utils.AndroidUtils.launchActivity
import com.protonvpn.android.utils.openUrl
import com.xwray.groupie.ExpandableGroup
import com.xwray.groupie.Group
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.GroupieViewHolder
import com.xwray.groupie.Item
import com.xwray.groupie.Section
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import me.proton.core.presentation.utils.viewBinding
import javax.inject.Inject

@AndroidEntryPoint
class CountryListFragment : Fragment(R.layout.fragment_country_list) {

    private val binding by viewBinding(FragmentCountryListBinding::bind)
    private val viewModel: CountryListViewModel by activityViewModels()

    @Inject
    lateinit var promoOfferButtonActions: PromoOfferButtonActions
    @Inject
    lateinit var upgradeTelemetry: UpgradeTelemetry

    private val secureCoreSpeedInfoDialog = registerForActivityResult(
        createContract()
    ) { activateSc: Boolean ->
        if (activateSc) toggleSecureCore()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initList()
        initSecureCoreSwitch()
        viewModel.scrollToTop.asLiveData().observe(viewLifecycleOwner) {
            binding.list.scrollToPosition(0)
        }
        viewModel.state.asLiveData().observe(viewLifecycleOwner) { state ->
            updateListData(state.sections)
        }

        viewModel.dismissLoading.onEach {
            binding.loadingContainer.switchToEmpty()
        }.launchIn(lifecycleScope)

        binding.loadingContainer.setOnRefreshListener {
            viewModel.refreshServerList()
        }
    }

    private fun initSecureCoreSwitch() = with(binding) {
        switchSecureCore.switchClickInterceptor = {
            if (!isChecked && !viewModel.hasAccessToSecureCore()) {
                UpgradeDialogActivity.launch<UpgradeSecureCoreHighlightsFragment>(requireContext())
            } else if (!isChecked) {
                secureCoreSpeedInfoDialog.launch(Unit)
            } else {
                toggleSecureCore()
            }
            true
        }
        viewModel.secureCoreLiveData.observe(
            viewLifecycleOwner
        ) { isEnabled: Boolean? ->
            if (isEnabled != null) {
                switchSecureCore.isChecked = isEnabled
            }
        }
    }

    private fun toggleSecureCore() {
        ProtonLogger.logUiSettingChange(Setting.SECURE_CORE, "main screen")
        viewModel.toggleSecureCore(!binding.switchSecureCore.isChecked)
    }

    private fun initList() = with(binding.list) {
        val groupAdapter = GroupAdapter<GroupieViewHolder>()
        val groupLayoutManager = GridLayoutManager(context, groupAdapter.spanCount).apply {
            spanSizeLookup = groupAdapter.spanSizeLookup
        }
        adapter = groupAdapter
        layoutManager = groupLayoutManager
        (itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
    }

    private fun updateListData(sections: List<ServerListSectionModel>) {
        val newGroups = mutableListOf<Group>()
        val groupAdapter = binding.list.adapter as GroupAdapter<GroupieViewHolder>

        val expandedGroupsIds = getExpandedGroupsIds(groupAdapter)
        for (section in sections) {
            section.getHeader(requireContext())?.let { text ->
                newGroups.add(
                    Section(HeaderViewHolder(
                        text = text,
                        infoButtonAction = section.infoType?.toAction()
                    ))
                )
            }
            section.items.forEach {
                newGroups.add(it, expandedGroupsIds)
            }
        }
        groupAdapter.replaceAll(newGroups)
    }

    fun MutableList<Group>.add(item: ServerListItemModel, expandedGroupsIds: Set<Long>) = when (item) {
        is FastestConnectionModel ->
            add(FastestConnectionItem(viewModel, viewLifecycleOwner, item))
        is CollapsibleServerGroupModel ->
            add(getExpandableGroup(item, expandedGroupsIds))
        is FreeUpsellBannerModel -> {
            val upsellItem = FreeUpsellItem {
                UpgradeDialogActivity.launch<UpgradePlusCountriesHighlightsFragment>(requireContext())
            }
            add(Section(upsellItem))
        }
    }

    private fun ServerListSectionModel.InfoType.toAction() : (() -> Unit) = when(this) {
        ServerListSectionModel.InfoType.FreeConnections -> {
            // Returns lambda
            { /* Not implemented */ }
        }
    }

    private fun getExpandableGroup(
        item: CollapsibleServerGroupModel,
        expandedGroupsIds: Set<Long>
    ) : ExpandableGroup {
        val groupVH = createGroupVH(item)
        return ExpandableGroup(groupVH).apply {
            isExpanded =
                groupVH.id in expandedGroupsIds && item.hasAccessibleOnlineServer
            item.sections.forEach { (title, servers) ->
                title?.let {
                    val titleString = resources.getString(it.titleRes, servers.size)
                    add(HeaderItem(titleString, true, it.infoType))
                }
                servers.forEach { server ->
                    add(
                        ServerGroupServerViewHolder(
                            viewModel,
                            server,
                            item,
                            viewLifecycleOwner,
                            title?.titleRes == R.string.listFastestServer,
                            item.sectionId
                        )
                    )
                }
            }
        }
    }

    private fun createGroupVH(serverGroup: CollapsibleServerGroupModel) =
        object : ServerGroupHeaderViewHolder(
            viewModel, serverGroup, viewLifecycleOwner
        ) {
            override fun onExpanded(position: Int) {
                if (!serverGroup.secureCore) {
                    val layoutManager =
                        this@CountryListFragment.binding.list.layoutManager as LinearLayoutManager
                    layoutManager.scrollToPositionWithOffset(position, 0)
                }
            }
        }

    private fun getExpandedGroupsIds(groupAdapter: GroupAdapter<GroupieViewHolder>) =
        with(groupAdapter) {
            (0 until groupCount).asSequence().map { getTopLevelGroup(it) }
                .filterIsInstance<ExpandableGroup>().filter { it.isExpanded }
                .mapNotNullTo(HashSet()) {
                    // The 0th item is the "parent", in this case CountryViewHolder.
                    (it.getGroup(0) as? Item<*>)?.id
                }
        }
}
