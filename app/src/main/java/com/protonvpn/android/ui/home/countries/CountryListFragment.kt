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
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.asLiveData
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.protonvpn.android.R
import com.protonvpn.android.api.NetworkLoader
import com.protonvpn.android.components.LoaderUI
import com.protonvpn.android.components.NetworkFrameLayout
import com.protonvpn.android.databinding.FragmentCountryListBinding
import com.protonvpn.android.models.vpn.VpnCountry
import com.protonvpn.android.ui.home.InformationActivity
import com.protonvpn.android.utils.Log
import com.xwray.groupie.ExpandableGroup
import com.xwray.groupie.Group
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.GroupieViewHolder
import com.zhuinden.fragmentviewbindingdelegatekt.viewBinding
import dagger.hilt.android.AndroidEntryPoint
import me.proton.core.network.domain.ApiResult

private const val SECTION_REGULAR = "regular"
private const val SECTION_DEDICATED_IPS = "dedicatedIps"

@AndroidEntryPoint
class CountryListFragment : Fragment(R.layout.fragment_country_list), NetworkLoader {

    private val binding by viewBinding(FragmentCountryListBinding::bind)
    private val viewModel: CountryListViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initList()
        observeLiveEvents()
        binding.loadingContainer.setOnRefreshListener { viewModel.refreshServerList(this) }
    }

    private fun observeLiveEvents() {
        viewModel.userDataUpdateEvent.observe(viewLifecycleOwner) {
            updateListData()
            if (viewModel.isFreeUser)
                binding.list.scrollToPosition(0)
        }
        viewModel.serverListVersion.asLiveData().observe(viewLifecycleOwner, Observer {
            updateListData()
        })
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

    private fun addCountriesGroup(
        groups: MutableList<Group>,
        @StringRes header: Int?,
        countries: List<VpnCountry>,
        expandedCountriesIds: Set<Long>,
        showServerSections: Boolean = true,
        groupId: String = SECTION_REGULAR,
    ) {
        val headerItem = header?.let { titleRes ->
            val headerTitle = resources.getString(titleRes, countries.size)
            HeaderItem(headerTitle, false, null)
        }
        addCountriesGroup(groups, headerItem, countries, expandedCountriesIds, showServerSections, groupId)
    }

    private fun addCountriesGroup(
        groups: MutableList<Group>,
        header: HeaderItem?,
        countries: List<VpnCountry>,
        expandedCountriesIds: Set<Long>,
        showServerSections: Boolean = true,
        groupId: String = SECTION_REGULAR,
    ) {
        if (header != null) {
            groups.add(header)
        }

        for (country in countries) {
            val expandableHeaderItem = object : CountryViewHolder(viewModel, country, groupId, viewLifecycleOwner) {
                override fun onExpanded(position: Int) {
                    if (!viewModel.isSecureCoreEnabled) {
                        val layoutManager =
                            this@CountryListFragment.binding.list.layoutManager as LinearLayoutManager
                        layoutManager.scrollToPositionWithOffset(position, 0)
                    }
                }
            }

            groups.add(ExpandableGroup(expandableHeaderItem).apply {
                isExpanded = expandableHeaderItem.id in expandedCountriesIds &&
                    viewModel.hasAccessibleOnlineServer(country)
                viewModel.getMappedServersForCountry(country, showServerSections).forEach { (title, servers) ->
                    title?.let {
                        val titleString = resources.getString(it.titleRes, servers.size)
                        add(HeaderItem(titleString, true, it.infoType))
                    }
                    servers.forEach {
                        add(CountryExpandedViewHolder(
                            viewModel, it, viewLifecycleOwner, title?.titleRes == R.string.listFastestServer))
                    }
                }
            })
        }
    }

    private fun updateListData() {
        val newGroups = mutableListOf<Group>()
        val groupAdapter = binding.list.adapter as GroupAdapter<GroupieViewHolder>

        val expandedCountriesIds = getExpandedCountriesIds(groupAdapter)
        if (viewModel.isFreeUser && !viewModel.isSecureCoreEnabled) {
            val (free, premium) = viewModel.getFreeAndPremiumCountries()
            addCountriesGroup(newGroups, R.string.listFreeCountries, free, expandedCountriesIds)
            addCountriesGroup(newGroups, R.string.listPremiumCountries_new_plans, premium, expandedCountriesIds)
        } else {
            val dedicatedIpCountries = viewModel.getDedicatedIpCountriesForList()
            if (dedicatedIpCountries.isNotEmpty()) {
                val headerText = resources.getString(R.string.listGatewayCountries, dedicatedIpCountries.size)
                val header = HeaderItem(headerText, false, InformationActivity.InfoType.DedicatedIps)
                addCountriesGroup(
                    newGroups,
                    header,
                    dedicatedIpCountries,
                    expandedCountriesIds,
                    showServerSections = false,
                    groupId = SECTION_DEDICATED_IPS
                )
            }
            addCountriesGroup(
                newGroups,
                R.string.listAllCountries.takeIf { dedicatedIpCountries.isNotEmpty() },
                viewModel.getCountriesForList(),
                expandedCountriesIds
            )
        }
        groupAdapter.update(newGroups)
    }

    private fun getExpandedCountriesIds(groupAdapter: GroupAdapter<GroupieViewHolder>) = with(groupAdapter) {
        (0 until groupCount).asSequence()
                .filter { (getGroup(it) as? ExpandableGroup)?.isExpanded == true }
                .mapTo(HashSet()) { getItem(it).id }
    }

    override fun getNetworkFrameLayout(): LoaderUI {
        try {
            return binding.loadingContainer
        } catch (e: IllegalStateException) {
            // FIXME: getNetworkFrameLayout is called from network callbacks that are unaware of
            //  views lifecycles, this needs to be refactored, for now return fake LoaderUI.
            Log.exception(e)
            return object : LoaderUI {
                override val state: NetworkFrameLayout.State =
                        NetworkFrameLayout.State.EMPTY

                override fun switchToLoading() {}
                override fun setRetryListener(listener: () -> Unit) {}
                override fun switchToEmpty() {}
                override fun switchToRetry(error: ApiResult.Error) {}
            }
        }
    }
}
