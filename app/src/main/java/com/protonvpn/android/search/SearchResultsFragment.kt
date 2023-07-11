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
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.StringRes
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.color.MaterialColors
import com.protonvpn.android.R
import com.protonvpn.android.components.VpnUiDelegateProvider
import com.protonvpn.android.databinding.FragmentSearchResultsBinding
import com.protonvpn.android.databinding.SearchEmptyHintBinding
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.models.vpn.VpnCountry
import com.protonvpn.android.ui.HeaderViewHolder
import com.protonvpn.android.ui.planupgrade.UpgradePlusCountriesDialogActivity
import com.protonvpn.android.ui.showDialogWithDontShowAgain
import com.protonvpn.android.utils.HtmlTools
import com.protonvpn.android.utils.ViewUtils.toPx
import com.protonvpn.android.utils.preventClickTrough
import com.protonvpn.android.utils.toStringHtmlColorNoAlpha
import com.xwray.groupie.Group
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.GroupieViewHolder
import com.xwray.groupie.Section
import com.zhuinden.fragmentviewbindingdelegatekt.viewBinding
import kotlinx.coroutines.launch
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

        viewModel.viewState.asLiveData().observe(viewLifecycleOwner) { updateState(it) }

        viewLifecycleOwner.lifecycleScope.launch {
            if (viewModel.getSecureCore()) {
                binding.emptyStateHints.addEmptyHint(R.string.search_empty_hint_countries_secure_core)
            } else {
                with(binding.emptyStateHints) {
                    addEmptyHint(R.string.search_empty_hint_countries)
                    addEmptyHint(R.string.search_empty_hint_cities)
                    addEmptyHint(R.string.search_empty_hint_usa_regions)
                    addEmptyHint(R.string.search_empty_hint_servers)
                }
            }
        }
    }

    private fun LinearLayout.addEmptyHint(@StringRes textRes: Int) {
        val textView = SearchEmptyHintBinding.inflate(layoutInflater).root
        val icon = ResourcesCompat.getDrawable(resources, R.drawable.ic_proton_magnifier, null)!!.mutate().apply {
            bounds = Rect(0, 0, HINT_SEARCH_ICON_DP.toPx(), HINT_SEARCH_ICON_DP.toPx())
        }
        textView.setCompoundDrawablesRelative(icon, null, null, null)
        textView.text = HtmlTools.fromHtml(
            getString(textRes, MaterialColors.getColor(textView, R.attr.proton_text_hint).toStringHtmlColorNoAlpha())
        )
        addView(textView)
    }

    private fun updateState(viewState: SearchViewModel.ViewState) {
        with(binding) {
            layoutEmptyState.isVisible = false
            layoutEmptyResult.isVisible = false
            layoutResultsState.isVisible = false
            when (viewState) {
                is SearchViewModel.ViewState.SearchHistory -> {
                    layoutResultsState.isVisible = true
                    setSearchRecentResults(viewState)
                }
                is SearchViewModel.ViewState.Empty -> {
                    layoutEmptyState.isVisible = true
                    resultsAdapter.clear()
                }
                is SearchViewModel.ViewState.EmptyResult -> {
                    layoutEmptyResult.isVisible = true
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
        if (state.showUpgradeBanner) {
            sections.add(Section(UpgradeBannerItem(viewModel.countryCount, this::showUpgrade)))
        }
        addSection(sections, R.string.server_search_countries_header, state.countries) {
            CountryResultBinding(it, matchLength, ::connectCountry, viewModel::disconnect, this::showUpgrade)
        }
        addSection(sections, R.string.server_search_cities_header, state.cities) {
            CityResultBinding(it, matchLength, ::connectCity, viewModel::disconnect, this::showUpgrade)
        }
        addSection(sections, R.string.server_search_servers_header, state.servers) {
            ServerResultBinding(it, matchLength, ::connectServer, viewModel::disconnect, this::showUpgrade)
        }
        resultsAdapter.updateAsync(sections)
    }

    private fun setSecureCoreResults(query: String, state: SearchViewModel.ViewState.ScSearchResults) {
        val sections = mutableListOf<Section>()
        addSection(sections, R.string.server_search_sc_countries_header, state.servers) {
            SecureCoreServerResultBinding(it, query.length, ::connectServer, viewModel::disconnect, this::showUpgrade)
        }
        resultsAdapter.updateAsync(sections)
    }

    private fun setSearchRecentResults(state: SearchViewModel.ViewState.SearchHistory) {
        val sections = mutableListOf<Section>()
        sections.add(
            Section(RecentsHeaderViewHolder(text = getString(
                R.string.search_recents_header_title,
                state.queries.size
            ),
                onClear = {
                    showDialogWithDontShowAgain(
                        context = requireContext(),
                        title = null,
                        message = getString(R.string.search_clear_history_dialog),
                        positiveButtonRes = R.string.dialogContinue,
                        negativeButtonRes = R.string.cancel,
                        showDialogPrefsKey = PREF_DONT_SHOW_CLEAR_HISTORY,
                        learnMoreUrl = null,
                        onAccepted = { viewModel.clearRecentHistory() }
                    )
                }),
                state.queries.map {
                    RecentResultBinding(
                        item = it,
                        onClick = viewModel::setQueryFromRecents
                    )
                })
        )
        resultsAdapter.updateAsync(sections)
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

    companion object {
        private const val HINT_SEARCH_ICON_DP = 16
        private const val PREF_DONT_SHOW_CLEAR_HISTORY = "PREF_CLEAR_HISTORY"
    }
}
