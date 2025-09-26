/*
 * Copyright (c) 2021 Proton AG
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

package com.protonvpn.android.tv.detailed

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.ObjectAdapter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.PresenterSelector
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.lifecycle.Observer
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseTvBrowseFragment
import com.protonvpn.android.components.VpnUiDelegateProvider
import com.protonvpn.android.databinding.TvServerRowBinding
import com.protonvpn.android.models.features.PaidFeature
import com.protonvpn.android.tv.detailed.TvServerListScreenFragment.Companion.EXTRA_COUNTRY
import com.protonvpn.android.tv.presenters.AbstractCardPresenter
import com.protonvpn.android.tv.ui.TvKeyConstants
import com.protonvpn.android.tv.upsell.TvUpsellActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TvServerListFragment : BaseTvBrowseFragment() {

    private val viewModel by viewModels<TvServerListViewModel>()
    private var rowsAdapter: ArrayObjectAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null)
            viewModel.init(requireArguments()[EXTRA_COUNTRY] as String)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rowsAdapter = ArrayObjectAdapter(ServerListRowPresenter())
        adapter = rowsAdapter

        viewModel.recents.observe(viewLifecycleOwner, Observer {
            updateRecents(it)
        })
        viewModel.servers.observe(viewLifecycleOwner, Observer {
            updateServerList(it)
        })

        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            require(item is TvServerListViewModel.ServerViewModel)
            item.click(
                (requireActivity() as VpnUiDelegateProvider).getVpnUiDelegate(),
                onUpgrade = {
                    val intent = Intent(context, TvUpsellActivity::class.java).apply {
                        putExtra(TvKeyConstants.PAID_FEATURE, PaidFeature.AllCountries)
                    }

                    requireContext().startActivity(intent)
                }
            )
        }

        startEntranceTransition()
    }

    override fun onDestroyView() {
        rowsAdapter = null
        super.onDestroyView()
    }

    private fun haveRecentsRow(): Boolean = rowsAdapter?.run {
        size() > 0 && (get(0) as ServersListRow).group == TvServerListViewModel.ServerGroup.Recents
    } ?: false

    private fun updateRecents(recents: List<TvServerListViewModel.ServerViewModel>) {
        if (recents.isEmpty()) {
            if (haveRecentsRow())
                rowsAdapter?.removeItems(0, 1)
        } else {
            val newRow = createRow(TvServerListViewModel.ServerGroup.Recents, recents, 0)
            if (haveRecentsRow())
                rowsAdapter?.replace(0, newRow)
            else
                rowsAdapter?.add(0, newRow)
        }

        // Update indices in all rows
        rowsAdapter?.unmodifiableList<ServersListRow>()?.forEachIndexed { index, serversListRow ->
            serversListRow.index = index
        }
    }

    private fun updateServerList(serverModel: TvServerListViewModel.ServersViewModel) = rowsAdapter?.apply {
        clear()
        viewModel.recents.value?.let(::updateRecents)
        serverModel.servers.onEach { (group, servers) ->
            add(createRow(group, servers, size()))
        }
    }

    private fun createRow(
        group: TvServerListViewModel.ServerGroup,
        servers: List<TvServerListViewModel.ServerViewModel>,
        index: Int
    ): Row {
        val listRowAdapter = ArrayObjectAdapter(ServersPresenterSelector(requireContext()))

        servers.forEach(listRowAdapter::add)

        return ServersListRow(null, listRowAdapter, group, index)
    }

    private fun TvServerListViewModel.ServerGroup.toLabel() = when (this) {
        TvServerListViewModel.ServerGroup.Recents -> getString(R.string.tv_recently_used_servers)
        TvServerListViewModel.ServerGroup.Available -> getString(R.string.tv_available_servers)
        TvServerListViewModel.ServerGroup.Locked -> getString(R.string.tv_locked_servers)
        TvServerListViewModel.ServerGroup.Other -> getString(R.string.tv_other_servers)
        is TvServerListViewModel.ServerGroup.City -> name
    }

    private class RowViewHolder(val binding: TvServerRowBinding, presenter: ListRowPresenter) :
        ListRowPresenter.ViewHolder(binding.root, binding.rowContent, presenter)

    private inner class ServerListRowPresenter : FadeListRowPresenter(false) {
        override fun rowAlpha(index: Int, selectedIdx: Int) = when {
            index < selectedIdx - 1 -> 0f
            index == selectedIdx - 1 -> BASE_INACTIVE_ROW_ALPHA
            index > selectedIdx -> BASE_INACTIVE_ROW_ALPHA / (index - selectedIdx)
            else -> 1f
        }

        override fun RowPresenter.ViewHolder.getRowIndex() =
            (rowObject as ServersListRow).index

        override fun createRowViewHolder(parent: ViewGroup): RowPresenter.ViewHolder {
            super.createRowViewHolder(parent)
            val rowView = TvServerRowBinding.inflate(layoutInflater).apply {
                rowContent.setHasFixedSize(false)
            }
            return RowViewHolder(rowView, this)
        }

        override fun onBindRowViewHolder(holder: RowPresenter.ViewHolder, item: Any) {
            super.onBindRowViewHolder(holder, item)

            with((holder as RowViewHolder).binding) {
                this.rowLabel.text = (item as? ServersListRow)
                    ?.run { group.toLabel() }
                    .orEmpty()
            }
        }
    }

    inner class ServersPresenterSelector(context: Context) : PresenterSelector() {
        private val presenter = ServerPresenter(context)
        override fun getPresenter(item: Any?): Presenter = presenter
    }

    class ServersListRow(
        header: HeaderItem?,
        adapter: ObjectAdapter,
        val group: TvServerListViewModel.ServerGroup,
        var index: Int = 0,
    ) : ListRow(header, adapter)

    inner class ServerPresenter(context: Context) :
        AbstractCardPresenter<TvServerListViewModel.ServerViewModel, TvServerCardView>(context) {

        override fun onCreateView() = TvServerCardView(context, viewLifecycleOwner)

        override fun onBindViewHolder(card: TvServerListViewModel.ServerViewModel, cardView: TvServerCardView) {
            cardView.bind(card)
        }

        override fun onUnbindViewHolder(cardView: TvServerCardView) {
            cardView.unbind()
        }
    }

    companion object {
        private const val BASE_INACTIVE_ROW_ALPHA = 0.6f
    }
}
