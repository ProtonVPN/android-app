/*
 * Copyright (c) 2022. Proton AG
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

package com.protonvpn.android.search

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.asLiveData
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.protonvpn.android.R
import com.protonvpn.android.components.VpnUiDelegateProvider
import com.protonvpn.android.databinding.FragmentSearchResultsBinding
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.models.vpn.VpnCountry
import com.protonvpn.android.ui.HeaderViewHolder
import com.protonvpn.android.ui.planupgrade.UpgradePlusCountriesDialogActivity
import com.protonvpn.android.utils.preventClickTrough
import com.xwray.groupie.Group
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.GroupieViewHolder
import com.xwray.groupie.Section
import com.zhuinden.fragmentviewbindingdelegatekt.viewBinding
import me.proton.core.util.kotlin.exhaustive

class SearchResultsFragment : Fragment(R.layout.fragment_search_results) {

    private val binding by viewBinding(FragmentSearchResultsBinding::bind)
    private val viewModel: SearchViewModel by viewModels({ requireActivity() })
    private lateinit var resultsAdapter: GroupAdapter<GroupieViewHolder>

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        resultsAdapter = GroupAdapter()
        binding.root.preventClickTrough()
        with(binding.listResults) {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = resultsAdapter
            (itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        }

        viewModel.viewState.asLiveData().observe(
            viewLifecycleOwner,
            Observer { updateState(it) }
        )
    }

    private fun updateState(viewState: SearchViewModel.ViewState) {
        with(binding) {
            layoutEmptyState.isVisible = false
            layoutResultsState.isVisible = false
            when (viewState) {
                is SearchViewModel.ViewState.SearchHistory -> {
                    // TODO: implement
                }
                is SearchViewModel.ViewState.Empty -> {
                    layoutEmptyState.isVisible = true
                    resultsAdapter.clear()
                }
                is SearchViewModel.ViewState.SearchResults -> {
                    layoutResultsState.isVisible = true
                    setResults(viewState.query, viewState)
                }
                is SearchViewModel.ViewState.ScSearchResults -> {
                    layoutResultsState.isVisible = true
                    setSecureCoreResults(viewState.query, viewState)
                }

            }.exhaustive
        }
    }

    private fun setResults(query: String, state: SearchViewModel.ViewState.SearchResults) {
        val matchLength = query.length
        val sections = mutableListOf<Section>()
        addSection(sections, R.string.server_search_countries_header, state.countries) {
            CountryResultBinding(it, matchLength, ::connectCountry, viewModel::disconnect, this::showUpgrade)
        }
        addSection(sections, R.string.server_search_cities_header, state.cities) {
            CityResultBinding(it, matchLength, ::connectCity, viewModel::disconnect, this::showUpgrade)
        }
        addSection(sections, R.string.server_search_servers_header, state.servers) {
            ServerResultBinding(it, matchLength, ::connectServer, viewModel::disconnect, this::showUpgrade)
        }
        resultsAdapter.update(sections)
    }

    private fun setSecureCoreResults(query: String, state: SearchViewModel.ViewState.ScSearchResults) {
        val sections = mutableListOf<Section>()
        addSection(sections, R.string.server_search_sc_countries_header, state.servers) {
            SecureCoreServerResultBinding(it, query.length, ::connectServer, viewModel::disconnect, this::showUpgrade)
        }
        resultsAdapter.update(sections)
    }

    private fun <T> addSection(
        sections: MutableList<Section>,
        @StringRes title: Int,
        items: Collection<T>,
        mapToBinding: (T) -> Group
    ) {
        if (items.isNotEmpty()) {
            sections.add(
                Section(HeaderViewHolder(text = getString(title, items.size)), items.map(mapToBinding))
            )
        }
    }

    private fun connectCountry(item: SearchViewModel.ResultItem<VpnCountry>) {
        viewModel.connectCountry((requireActivity() as VpnUiDelegateProvider).getVpnUiDelegate(), item)
    }

    private fun connectCity(item: SearchViewModel.ResultItem<List<Server>>) {
        viewModel.connectCity((requireActivity() as VpnUiDelegateProvider).getVpnUiDelegate(), item)
    }

    private fun connectServer(item: SearchViewModel.ResultItem<Server>) {
        viewModel.connectServer((requireActivity() as VpnUiDelegateProvider).getVpnUiDelegate(), item)
    }

    private fun showUpgrade() {
        startActivity(Intent(requireContext(), UpgradePlusCountriesDialogActivity::class.java));
    }
}
