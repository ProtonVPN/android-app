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
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.protonvpn.android.R
import com.protonvpn.android.api.NetworkLoader
import com.protonvpn.android.components.LoaderUI
import com.protonvpn.android.components.NetworkFrameLayout
import com.protonvpn.android.databinding.FragmentCountryListBinding
import com.protonvpn.android.databinding.ItemFreeUpsellBinding
import com.protonvpn.android.models.vpn.ServerGroup
import com.protonvpn.android.ui.HeaderViewHolder
import com.protonvpn.android.ui.home.FreeConnectionsInfoActivity
import com.protonvpn.android.ui.home.InformationActivity
import com.protonvpn.android.ui.planupgrade.UpgradePlusCountriesDialogActivity
import com.protonvpn.android.utils.AndroidUtils.launchActivity
import com.protonvpn.android.utils.Log
import com.protonvpn.android.utils.ViewUtils.toPx
import com.xwray.groupie.ExpandableGroup
import com.xwray.groupie.Group
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.GroupieViewHolder
import com.xwray.groupie.Item
import com.xwray.groupie.Section
import com.xwray.groupie.viewbinding.BindableItem
import com.zhuinden.fragmentviewbindingdelegatekt.viewBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import me.proton.core.network.domain.ApiResult

private const val SECTION_REGULAR = "regular"
private const val SECTION_GATEWAYS = "gateways"

@AndroidEntryPoint
class CountryListFragment : Fragment(R.layout.fragment_country_list), NetworkLoader {

    private val binding by viewBinding(FragmentCountryListBinding::bind)
    private val viewModel: CountryListViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initList()
        observeState()
        binding.loadingContainer.setOnRefreshListener { viewModel.refreshServerList(this) }
    }

    private fun observeState() {
        viewModel.updateListFlow.onEach { action ->
            updateListData()
            if (action == ListUpdateEvent.REFRESH_AND_SCROLL) {
                binding.list.scrollToPosition(0)
            }
        }.launchIn(viewLifecycleOwner.lifecycleScope)
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

    private suspend fun addCountriesGroup(
        groups: MutableList<Group>,
        @StringRes header: Int?,
        serverGroups: List<ServerGroup>,
        expandedCountriesIds: Set<Long>,
        groupId: String = SECTION_REGULAR,
        headerGroups: List<Group>? = null
    ) {
        val headerItem = header?.let { titleRes ->
            val headerTitle = resources.getString(titleRes, serverGroups.size)
            HeaderItem(headerTitle, false, null)
        }
        addCountriesGroup(
            groups,
            headerItem,
            serverGroups,
            expandedCountriesIds,
            groupId,
            headerGroups
        )
    }

    private suspend fun addCountriesGroup(
        groups: MutableList<Group>,
        header: HeaderItem?,
        serverGroups: List<ServerGroup>,
        expandedCountriesIds: Set<Long>,
        groupId: String = SECTION_REGULAR,
        headerGroups: List<Group>? = null
    ) {
        if (header != null) {
            groups.add(header)
        }
        headerGroups?.let {
            groups.addAll(it)
        }
        val restricted = viewModel.isServerListRestricted()
        for (group in serverGroups) {
            val expandableHeaderItem =
                getCountryViewHolder(group, groupId, restricted)

            groups.add(ExpandableGroup(expandableHeaderItem).apply {
                isExpanded =
                    expandableHeaderItem.id in expandedCountriesIds && viewModel.hasAccessibleOnlineServer(
                        group
                    )
                viewModel.getMappedServersForGroup(group).forEach { (title, servers) ->
                    title?.let {
                        val titleString = resources.getString(it.titleRes, servers.size)
                        add(HeaderItem(titleString, true, it.infoType))
                    }
                    servers.forEach {
                        add(
                            CountryExpandedViewHolder(
                                viewModel,
                                it,
                                viewLifecycleOwner,
                                title?.titleRes == R.string.listFastestServer,
                                groupId
                            )
                        )
                    }
                }
            })
        }
    }

    private fun getCountryViewHolder(
        group: ServerGroup,
        groupId: String = SECTION_REGULAR,
        restricted: Boolean,
    ): CountryViewHolder {
        val isOnline = !group.isUnderMaintenance()
        val userHasAccess = viewModel.hasAccessibleServer(group)
        val accessibleAndOnline = userHasAccess && isOnline && !restricted
        val expandableCountry =
            object : CountryViewHolder(
                viewModel, group, groupId, accessibleAndOnline, viewLifecycleOwner
            ) {
                override fun onExpanded(position: Int) {
                    if (!viewModel.isSecureCoreEnabled) {
                        val layoutManager =
                            this@CountryListFragment.binding.list.layoutManager as LinearLayoutManager
                        layoutManager.scrollToPositionWithOffset(position, 0)
                    }
                }
            }
        return expandableCountry
    }

    private suspend fun updateListForFree(
        newGroups: MutableList<Group>, expandedCountriesIds: HashSet<Long>
    ) {
        val isNewFree = viewModel.isServerListRestricted()
        val (free, premium) = viewModel.getFreeAndPremiumCountries()
        val lockedCountries = if (isNewFree) viewModel.getCountriesForList() else premium

        val recommendedConnections = viewModel.getRecommendedConnections()
        if (recommendedConnections.isNotEmpty()) {
            newGroups.add(Section(HeaderViewHolder(R.string.free_connection_header) {
                activity?.launchActivity<FreeConnectionsInfoActivity>()
            }))
            recommendedConnections.forEach {
                newGroups.add(RecommendedConnectionItem(viewModel, viewLifecycleOwner, it))
            }
        } else {
            addCountriesGroup(
                newGroups,
                R.string.listFreeCountries,
                free,
                expandedCountriesIds
            )
        }
        addCountriesGroup(
            newGroups,
            R.string.listPremiumCountries_new_plans,
            lockedCountries,
            expandedCountriesIds,
            headerGroups = if (isNewFree) {
                listOf(Section(FreeUpsellItem(countrySize = lockedCountries.size, onClick = {
                    requireContext().launchActivity<UpgradePlusCountriesDialogActivity>()
                })))
            } else {
                emptyList()
            }
        )
    }

    private fun updateListData() {
        viewLifecycleOwner.lifecycleScope.launch {
            val newGroups = mutableListOf<Group>()
            val groupAdapter = binding.list.adapter as GroupAdapter<GroupieViewHolder>

            val expandedCountriesIds = getExpandedCountriesIds(groupAdapter)
            if (viewModel.isFreeUser && !viewModel.isSecureCoreEnabled) {
                updateListForFree(newGroups, expandedCountriesIds)
            } else {
                val gatewayGroups = viewModel.getGatewayGroupsForList()
                if (gatewayGroups.isNotEmpty()) {
                    val headerText = resources.getString(R.string.listGateways)
                    val header =
                        HeaderItem(headerText, false, InformationActivity.InfoType.Gateways)
                    addCountriesGroup(
                        newGroups,
                        header,
                        gatewayGroups,
                        expandedCountriesIds,
                        groupId = SECTION_GATEWAYS
                    )
                }
                addCountriesGroup(
                    newGroups,
                    R.string.listAllCountries.takeIf { gatewayGroups.isNotEmpty() },
                    viewModel.getCountriesForList(),
                    expandedCountriesIds
                )
            }
            groupAdapter.replaceAll(newGroups)
        }
    }

    private fun getExpandedCountriesIds(groupAdapter: GroupAdapter<GroupieViewHolder>) =
        with(groupAdapter) {
            (0 until groupCount).asSequence().map { getTopLevelGroup(it) }
                .filterIsInstance<ExpandableGroup>().filter { it.isExpanded }
                .mapNotNullTo(HashSet()) {
                    // The 0th item is the "parent", in this case CountryViewHolder.
                    (it.getGroup(0) as? Item<*>)?.id
                }
        }

    override fun getNetworkFrameLayout(): LoaderUI {
        try {
            return binding.loadingContainer
        } catch (e: IllegalStateException) {
            // FIXME: getNetworkFrameLayout is called from network callbacks that are unaware of
            //  views lifecycles, this needs to be refactored, for now return fake LoaderUI.
            Log.exception(e)
            return object : LoaderUI {
                override val state: NetworkFrameLayout.State = NetworkFrameLayout.State.EMPTY

                override fun switchToLoading() {}
                override fun setRetryListener(listener: () -> Unit) {}
                override fun switchToEmpty() {}
                override fun switchToRetry(error: ApiResult.Error) {}
            }
        }
    }

    class FreeUpsellItem(
        private val countrySize: Int, private val onClick: () -> Unit
    ) : BindableItem<ItemFreeUpsellBinding>(1) {
        override fun bind(binding: ItemFreeUpsellBinding, position: Int) = with(binding) {
            val resources = root.resources
            textTitle.text = resources.getString(R.string.free_upsell_header_title, countrySize)
            root.setOnClickListener { onClick() }
        }

        override fun getLayout(): Int = R.layout.item_free_upsell
        override fun initializeViewBinding(view: View) =
            ItemFreeUpsellBinding.bind(view).apply {
                root.layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    leftMargin = 16.toPx()
                    rightMargin = 16.toPx()
                }
            }
    }
}
