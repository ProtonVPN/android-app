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
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.asLiveData
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.protonvpn.android.R
import com.protonvpn.android.api.NetworkLoader
import com.protonvpn.android.components.LoaderUI
import com.protonvpn.android.components.NetworkFrameLayout
import com.protonvpn.android.databinding.FragmentCountryListBinding
import com.protonvpn.android.databinding.ItemFreeUpsellBinding
import com.protonvpn.android.ui.HeaderViewHolder
import com.protonvpn.android.ui.home.FreeConnectionsInfoActivity
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
import me.proton.core.network.domain.ApiResult

@AndroidEntryPoint
class CountryListFragment : Fragment(R.layout.fragment_country_list), NetworkLoader {

    private val binding by viewBinding(FragmentCountryListBinding::bind)
    private val viewModel: CountryListViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initList()

        viewModel.scrollToTop.asLiveData().observe(viewLifecycleOwner) {
            binding.list.scrollToPosition(0)
        }
        viewModel.state.asLiveData().observe(viewLifecycleOwner) { state ->
            updateListData(state.sections)
        }

        binding.loadingContainer.setOnRefreshListener { viewModel.refreshServerList(this) }
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
        is RecommendedConnectionModel ->
            add(RecommendedConnectionItem(viewModel, viewLifecycleOwner, item))
        is CollapsibleServerGroupModel ->
            add(getExpandableGroup(item, expandedGroupsIds))
        is UpsellBannerModel ->
            add(Section(
                FreeUpsellItem(countryCount = item.premiumCountriesCount) {
                    requireContext().launchActivity<UpgradePlusCountriesDialogActivity>()
                }
            ))
    }

    private fun ServerListSectionModel.InfoType.toAction() : (() -> Unit) = when(this) {
        ServerListSectionModel.InfoType.FreeConnections -> {
            // Returns lambda
            { activity?.launchActivity<FreeConnectionsInfoActivity>() }
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

    override fun getNetworkFrameLayout(): LoaderUI =
        try {
            binding.loadingContainer
        } catch (e: IllegalStateException) {
            // FIXME: getNetworkFrameLayout is called from network callbacks that are unaware of
            //  views lifecycles, this needs to be refactored, for now return fake LoaderUI.
            Log.exception(e)
            object : LoaderUI {
                override val state: NetworkFrameLayout.State = NetworkFrameLayout.State.EMPTY

                override fun switchToLoading() {}
                override fun setRetryListener(listener: () -> Unit) {}
                override fun switchToEmpty() {}
                override fun switchToRetry(error: ApiResult.Error) {}
            }
        }

    class FreeUpsellItem(
        private val countryCount: Int, private val onClick: () -> Unit
    ) : BindableItem<ItemFreeUpsellBinding>(1) {
        override fun bind(binding: ItemFreeUpsellBinding, position: Int) = with(binding) {
            val resources = root.resources
            textTitle.text = resources.getString(R.string.free_upsell_header_title, countryCount)
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
