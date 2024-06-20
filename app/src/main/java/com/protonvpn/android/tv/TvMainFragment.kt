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

import android.content.DialogInterface
import android.content.Intent
import android.graphics.Outline
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import androidx.core.content.ContextCompat
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.PresenterSelector
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.lifecycle.Observer
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseTvActivity
import com.protonvpn.android.components.BaseTvBrowseFragment
import com.protonvpn.android.databinding.TvCardRowBinding
import com.protonvpn.android.tv.detailed.CountryDetailFragment
import com.protonvpn.android.tv.main.TvMainViewModel
import com.protonvpn.android.tv.main.translateMapCoordinatesToRegion
import com.protonvpn.android.tv.models.CardListRow
import com.protonvpn.android.tv.models.CardRow
import com.protonvpn.android.tv.models.ConnectIntentCard
import com.protonvpn.android.tv.models.CountryCard
import com.protonvpn.android.tv.models.LogoutCard
import com.protonvpn.android.tv.models.QuickConnectCard
import com.protonvpn.android.tv.models.ReportBugCard
import com.protonvpn.android.tv.models.SettingsSplitTunnelingCard
import com.protonvpn.android.tv.presenters.CardPresenterSelector
import com.protonvpn.android.tv.presenters.TvItemCardView
import com.protonvpn.android.tv.settings.splittunneling.TvSettingsSplitTunnelingActivity
import com.protonvpn.android.ui.drawer.bugreport.DynamicReportActivity
import com.protonvpn.android.utils.AndroidUtils.isRtl
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.utils.ViewUtils.toPx
import com.protonvpn.android.utils.relativePadding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TvMainFragment : BaseTvBrowseFragment() {

    private val viewModel by activityViewModels<TvMainViewModel>()

    private var rowsAdapter: ArrayObjectAdapter? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        onItemViewSelectedListener = OnItemViewSelectedListener { _, item, _, _ ->
            if (item != null) {
                val selectedCountry = when (item) {
                    is CountryCard -> item.vpnCountry.flag
                    is QuickConnectCard -> viewModel.quickConnectFlag
                    is ConnectIntentCard -> item.connectCountry
                    else -> null
                }
                viewModel.setSelectedCountry(selectedCountry)
            }
        }

        setupUi()
        monitorVpnState()
        postponeEnterTransition()
        rowsAdapter = ArrayObjectAdapter(FadeTopListRowPresenter())
        adapter = rowsAdapter
        setupRowAdapter()
        viewModel.listVersion.asLiveData().observe(viewLifecycleOwner, Observer {
            setupRowAdapter()
        })
        lifecycleScope.launchWhenResumed {
            viewModel.userPlanChangeEvent.collect {
                setupRowAdapter()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.resetMap()
    }

    override fun onDestroyView() {
        rowsAdapter = null
        super.onDestroyView()
    }

    private fun setupUi() {
        onItemViewClickedListener = OnItemViewClickedListener { viewHolder, item, _, _ ->
            when (item) {
                is CountryCard -> {
                    if (item.vpnCountry.isUnderMaintenance()) {
                        viewModel.showMaintenanceDialog(requireContext())
                        return@OnItemViewClickedListener
                    }
                    val imageView = (viewHolder.view as TvItemCardView).binding.imageBackground

                    val countryName = CountryTools.codeToMapCountryName[item.vpnCountry.flag]
                    val bounds = CountryTools.tvMapNameToBounds[countryName]
                    if (bounds != null && countryName != null) {
                        viewModel.mapRegion.value = bounds
                            .relativePadding(.1f) // Padding relative to country size
                            .translateMapCoordinatesToRegion()
                    }

                    activity?.supportFragmentManager?.commit {
                        setReorderingAllowed(true)
                        addSharedElement(
                            imageView, CountryDetailFragment.transitionNameForCountry(item.vpnCountry.flag)
                        )
                        replace(
                            R.id.container,
                            CountryDetailFragment::class.java,
                            CountryDetailFragment.createArguments(item.vpnCountry.flag)
                        )
                        addToBackStack(null)
                    }
                }
                is ConnectIntentCard -> {
                    viewModel.connect(requireActivity() as BaseTvActivity, item)
                }
                is QuickConnectCard -> {
                    viewModel.onQuickConnectAction(requireActivity() as BaseTvActivity)
                }
                is SettingsSplitTunnelingCard -> {
                    startActivity(Intent(context, TvSettingsSplitTunnelingActivity::class.java))
                }
                is LogoutCard -> {
                    logout()
                }
                is ReportBugCard -> {
                    startActivity(Intent(context, DynamicReportActivity::class.java))
                }
            }
        }
    }

    private fun setupRowAdapter() {
        rowsAdapter?.createRows()
        view?.doOnPreDraw {
            startPostponedEnterTransition()
        }
    }

    private fun monitorVpnState() {
        viewModel.vpnConnectionState.observe(viewLifecycleOwner, Observer {
            rowsAdapter?.updateRecentsRow()
        })
    }

    private fun logout() {
        showTvDialog(requireContext(), focusedButton = DialogInterface.BUTTON_NEGATIVE) {
            setTitle(R.string.tv_signout_dialog_title)
            setPositiveButton(R.string.tv_signout_dialog_ok) { _, _ -> viewModel.logout() }
            setNegativeButton(R.string.cancel, null)
            if (viewModel.isConnected())
                setMessage(R.string.tv_signout_dialog_description_connected)
        }
    }

    private fun ArrayObjectAdapter.updateRecentsRow() {
        val recentsRow = CardRow(
            title = R.string.quickConnect,
            icon = R.drawable.ic_proton_power_off_32,
            cards = viewModel.getRecentCardList(requireContext()),
            tintIcon = true
        )
        addOrReplace(0, createRow(recentsRow, 0))
    }

    private fun ArrayObjectAdapter.createRows() {
        var index = 1
        updateRecentsRow()
        val continentMap = viewModel.getCountryCardMap()

        CountryTools.Continent.values().forEach { continent ->
            continentMap[continent]?.let { cards ->
                addOrReplace(index,
                    createRow(
                        CardRow(
                            title = continent.nameRes,
                            icon = continent.iconRes,
                            cards = cards
                        ),
                        index
                    )
                )
                index++
            }
        }

        val settingsRow = CardRow(
            title = R.string.tv_row_settings,
            icon = R.drawable.ic_proton_three_dots_horizontal_32,
            cards = listOf(
                SettingsSplitTunnelingCard(getString(R.string.tv_card_split_tunneling_label)),
                LogoutCard(getString(R.string.tv_signout_label)),
                ReportBugCard(getString(R.string.drawerReportProblem))
            ),
            tintIcon = true
        )
        addOrReplace(index, createRow(settingsRow, index))
        index++
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

    private inner class FadeTopListRowPresenter : FadeListRowPresenter(true) {

        override fun rowAlpha(index: Int, selectedIdx: Int) = when {
            index < selectedIdx - 1 -> 0f
            index == selectedIdx - 1 -> TOP_ROW_ALPHA
            else -> 1f
        }

        override fun RowPresenter.ViewHolder.getRowIndex() =
            (rowObject as CardListRow).index

        override fun createRowViewHolder(parent: ViewGroup): RowPresenter.ViewHolder {
            super.createRowViewHolder(parent)

            val rowView = TvCardRowBinding.inflate(layoutInflater)

            // Clip cards with fading edge before icon begins
            rowView.rowContent.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    if (requireActivity().isRtl())
                        outline.setRect(Int.MIN_VALUE, Int.MIN_VALUE, view.right, Int.MAX_VALUE)
                    else
                        outline.setRect(0, Int.MIN_VALUE, Int.MAX_VALUE, Int.MAX_VALUE)
                }

            }
            rowView.rowContent.clipToOutline = true
            if (requireActivity().isRtl()) {
                rowView.rowContent.fadingRightEdge = true
                rowView.rowContent.fadingRightEdgeLength = ROW_FADING_EDGE_DP.toPx()
            } else {
                rowView.rowContent.fadingLeftEdge = true
                rowView.rowContent.fadingLeftEdgeLength = ROW_FADING_EDGE_DP.toPx()
            }
            rowView.rowContent.setHasFixedSize(false)
            return RowViewHolder(rowView, this)
        }

        override fun onBindRowViewHolder(holder: RowPresenter.ViewHolder, item: Any?) {
            super.onBindRowViewHolder(holder, item)
            val row = (item as CardListRow).cardRow
            with(holder as RowViewHolder) {
                binding.icon.setImageResource(row.icon)
                binding.label.setText(row.title)
                if (row.tintIcon) {
                    binding.icon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.tvCardRowIconColor))
                } else {
                    binding.icon.colorFilter = null
                }
            }
        }

        override fun onRowViewSelected(holder: RowPresenter.ViewHolder?, selected: Boolean) {
            super.onRowViewSelected(holder, selected)
            val index = rowsAdapter?.indexOf(holder?.rowObject) ?: -1
            val isLastRow = index >= 0 && index == (rowsAdapter?.size() ?: 0) - 1
            if (isLastRow)
                viewModel.onLastRowSelection(selected)
        }
    }

    companion object {
        private const val ROW_FADING_EDGE_DP = 16
        private const val TOP_ROW_ALPHA = 0.5f
    }
}
