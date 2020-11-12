/*
 * Copyright (c) 2020 Proton Technologies AG
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
package com.protonvpn.android.tv

import android.graphics.Outline
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import androidx.core.view.ViewCompat
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.commit
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.PresenterSelector
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseTvBrowseFragment
import com.protonvpn.android.databinding.TvCardRowBinding
import com.protonvpn.android.tv.detailed.CountryDetailFragment
import com.protonvpn.android.tv.main.TvMapRenderer
import com.protonvpn.android.tv.models.CardListRow
import com.protonvpn.android.tv.models.CardRow
import com.protonvpn.android.tv.models.CountryCard
import com.protonvpn.android.tv.models.DetailedIconCard
import com.protonvpn.android.tv.models.IconCard
import com.protonvpn.android.tv.models.ProfileCard
import com.protonvpn.android.tv.presenters.CardPresenterSelector
import com.protonvpn.android.ui.home.TvHomeViewModel
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.utils.DebugUtils.debugAssert
import com.protonvpn.android.utils.ViewUtils.toPx
import com.protonvpn.android.vpn.VpnState
import javax.inject.Inject

class TvMainFragment : BaseTvBrowseFragment() {

    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory

    private lateinit var viewModel: TvHomeViewModel

    private var rowsAdapter: ArrayObjectAdapter? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(requireActivity(), viewModelFactory).get(TvHomeViewModel::class.java)
        val haveCountries = viewModel.serverManager.getVpnCountries().isNotEmpty()

        onItemViewSelectedListener = OnItemViewSelectedListener { _, item, _, _ ->
            if (item != null)
                viewModel.selectedCountry.value = (item as? CountryCard)?.vpnCountry
        }

        setupUi()
        monitorVpnState()
        postponeEnterTransition()
        rowsAdapter = ArrayObjectAdapter(FadeTopListRowPresenter())
        adapter = rowsAdapter
        if (haveCountries)
            setupRowAdapter()
        viewModel.serverManager.updateEvent.observe(viewLifecycleOwner) {
            setupRowAdapter()
        }
    }

    override fun onDestroyView() {
        adapter = null
        rowsAdapter?.unregisterAllObservers()
        rowsAdapter = null
        super.onDestroyView()
    }

    private fun setupUi() {
        title = getString(R.string.tvMainTitle)

        onItemViewClickedListener = OnItemViewClickedListener { viewHolder, item, _, _ ->
            when (item) {
                is CountryCard -> {
                    val imageView = (viewHolder.view as ImageCardView).mainImageView
                    val bundle = Bundle().apply { putSerializable(CountryDetailFragment.EXTRA_CARD, item) }

                    CountryTools.locationMap[item.vpnCountry.flag]?.let {
                        val x = it.x * CountryTools.LOCATION_TO_TV_MAP_COORDINATES_RATIO / TvMapRenderer.WIDTH
                        val y = it.y * CountryTools.LOCATION_TO_TV_MAP_COORDINATES_RATIO / TvMapRenderer.WIDTH
                        viewModel.mapRegion.value = TvMapRenderer.MapRegion(
                                x.toFloat() - 0.25f, y.toFloat() - 0.13f, 0.5f)
                    }

                    activity?.supportFragmentManager?.commit {
                        setReorderingAllowed(true)
                        addSharedElement(imageView,
                                CountryDetailFragment.transitionNameForCountry(item.vpnCountry.flag))
                        replace(R.id.container, CountryDetailFragment::class.java, bundle)
                        addToBackStack(null)
                    }
                }
                is ProfileCard -> {
                    viewModel.connect(requireActivity(), item)
                }
                is DetailedIconCard -> {
                    viewModel.onQuickConnectAction(requireActivity())
                }

            }
        }
    }

    private fun setupRowAdapter() {
        createRows()
        view?.doOnPreDraw {
            headersState = HEADERS_DISABLED
            startPostponedEnterTransition()
        }
    }

    private fun monitorVpnState() {
        viewModel.vpnStatus.observe(viewLifecycleOwner, Observer {
            when (it) {
                VpnState.Connected -> {
                    updateRecentsRow()
                }
                VpnState.Disabled -> {
                    updateRecentsRow()
                }
            }
        })
    }

    private fun updateRecentsRow() {
        val recentsRow = CardRow(
            title = R.string.recents,
            icon = R.drawable.ic_recent,
            cards = viewModel.getRecentCardList(requireContext())
        )
        if (rowsAdapter!!.size() == 0) {
            rowsAdapter!!.add(createRow(recentsRow, 0))
        } else {
            rowsAdapter!!.replace(0, createRow(recentsRow, 0))
        }
    }

    private fun createRows() {
        var index = 1
        rowsAdapter?.clear()
        updateRecentsRow()
        val cards = viewModel.serverManager.getVpnCountries().groupBy({
            val continent = CountryTools.locationMap[it.flag]?.continent
            debugAssert { continent != null }
            continent
        }, { country ->
            CountryCard(
                country.countryName,
                CountryTools.getFlagResource(requireContext(), country.flag),
                country
            )
        })

        CountryTools.Continent.values().forEach {
            cards[it]?.let { cards ->
                rowsAdapter!!.add(
                    createRow(
                        CardRow(title = it.nameRes, icon = it.iconRes, cards = cards),
                        index++
                    )
                )
            }
        }

        val settingsRow = CardRow(
            title = R.string.tvRowMore,
            icon = R.drawable.row_more_icon,
            cards = listOf(IconCard(getString(R.string.drawerLogout), R.drawable.ic_drawer_logout)))
        rowsAdapter!!.add(createRow(settingsRow, index++))
    }

    private fun createRow(cardRow: CardRow, index: Int): Row {
        val presenterSelector: PresenterSelector = CardPresenterSelector(requireContext())
        val listRowAdapter = ArrayObjectAdapter(presenterSelector)
        for (card in cardRow.cards)
            listRowAdapter.add(card)
        return CardListRow(null, listRowAdapter, cardRow, index)
    }

    private class RowViewHolder(val binding: TvCardRowBinding, presenter: ListRowPresenter) :
            ListRowPresenter.ViewHolder(binding.root, binding.rowContent, presenter)

    private inner class FadeTopListRowPresenter : ListRowPresenter() {

        private var selectedIndex: Int? = null

        init {
            shadowEnabled = false
        }

        private fun RowPresenter.ViewHolder.setupAlpha(animated: Boolean) {
            val index = (rowObject as CardListRow).index
            val selectedIdx = selectedIndex ?: -1
            val targetAlpha = when {
                index < selectedIdx - 1 -> 0f
                index == selectedIdx - 1 -> 0.5f
                else -> 1f
            }
            (this.view.parent as? ViewGroup)?.apply {
                if (animated)
                    animate().alpha(targetAlpha).duration = ROW_FADE_DURATION
                else
                    alpha = targetAlpha
            }
        }

        override fun createRowViewHolder(parent: ViewGroup): RowPresenter.ViewHolder {
            super.createRowViewHolder(parent)

            val rowView = TvCardRowBinding.inflate(layoutInflater)

            // Clip cards with fading edge before icon begins
            rowView.rowContent.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    val ltr = ViewCompat.getLayoutDirection(rowView.root) == ViewCompat.LAYOUT_DIRECTION_LTR
                    if (ltr)
                        outline.setRect(0, Int.MIN_VALUE, Int.MAX_VALUE, Int.MAX_VALUE)
                    else
                        outline.setRect(Int.MIN_VALUE, Int.MIN_VALUE, view.right, Int.MAX_VALUE)
                }

            }
            rowView.rowContent.clipToOutline = true
            rowView.rowContent.fadingLeftEdge = true
            rowView.rowContent.fadingLeftEdgeLength = ROW_FADING_EDGE_DP.toPx()
            rowView.rowContent.setHasFixedSize(false)
            return RowViewHolder(rowView, this)
        }

        override fun onBindRowViewHolder(holder: RowPresenter.ViewHolder, item: Any?) {
            super.onBindRowViewHolder(holder, item)
            holder.setupAlpha(false)
            val row = (item as CardListRow).cardRow
            with(holder as RowViewHolder) {
                binding.icon.setImageResource(row.icon)
                binding.label.setText(row.title)
            }
        }

        override fun onRowViewSelected(holder: RowPresenter.ViewHolder?, selected: Boolean) {
            super.onRowViewSelected(holder, selected)

            if (selected) {
                selectedIndex = (holder?.rowObject as CardListRow).index
                (0 until adapter.size()).forEach { i ->
                    rowsSupportFragment.getRowViewHolder(i)?.setupAlpha(true)
                }
            }
        }
    }

    companion object {
        private const val ROW_FADE_DURATION = 300L
        private const val ROW_FADING_EDGE_DP = 16
    }
}
