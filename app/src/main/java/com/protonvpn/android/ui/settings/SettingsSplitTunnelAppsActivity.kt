/*
 * Copyright (c) 2021. Proton AG
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

package com.protonvpn.android.ui.settings

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.lifecycle.asLiveData
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.theme.enableEdgeToEdgeVpn
import com.protonvpn.android.databinding.ActivityRecyclerWithToolbarBinding
import com.protonvpn.android.databinding.ItemSplitTunnelAppsEmptyBinding
import com.protonvpn.android.databinding.ItemSplitTunnelAppsLoadSystemAppsBinding
import com.protonvpn.android.settings.data.SplitTunnelingMode
import com.protonvpn.android.ui.HeaderViewHolder
import com.protonvpn.android.ui.SaveableSettingsActivity
import com.protonvpn.android.utils.BindableItemEx
import com.protonvpn.android.utils.applySystemBarInsets
import com.protonvpn.android.utils.getSerializableExtraCompat
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.GroupieViewHolder
import com.xwray.groupie.Item
import com.xwray.groupie.OnAsyncUpdateListener
import com.xwray.groupie.Section
import com.xwray.groupie.viewbinding.BindableItem
import dagger.hilt.android.AndroidEntryPoint
import me.proton.core.presentation.R as CoreR

@AndroidEntryPoint
class SettingsSplitTunnelAppsActivity : SaveableSettingsActivity<SettingsSplitTunnelAppsViewModel>() {

    override val viewModel: SettingsSplitTunnelAppsViewModel by viewModels()

    private var previousSystemAppsState: SplitTunnelingAppsViewModelHelper.SystemAppsState? = null

    private lateinit var mode: SplitTunnelingMode

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdgeVpn()
        super.onCreate(savedInstanceState)
        val binding = ActivityRecyclerWithToolbarBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root)

        mode = requireNotNull(intent.getSerializableExtraCompat<SplitTunnelingMode>(SPLIT_TUNNELING_MODE_KEY))
        title = when (mode) {
            SplitTunnelingMode.INCLUDE_ONLY -> getString(R.string.settings_split_tunneling_included_apps)
            SplitTunnelingMode.EXCLUDE_ONLY -> getString(R.string.settings_split_tunneling_excluded_apps)
        }
        initToolbarWithUpEnabled(binding.contentAppbar.toolbar)

        val selectedItemsSection = Section()
        val availableItemsSection = Section()
        val itemsAdapter = GroupAdapter<GroupieViewHolder>().apply {
            add(selectedItemsSection)
            add(availableItemsSection)
        }

        val linearLayoutManager = LinearLayoutManager(this)
        with(binding.recyclerItems) {
            adapter = itemsAdapter
            layoutManager = linearLayoutManager
            // Avoid animating header updates.
            (itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        }

        val actionAdd = { item: LabeledItem -> viewModel.addApp(item) }
        val actionRemove = { item: LabeledItem -> viewModel.removeApp(item) }
        viewModel.viewState.asLiveData().observe(this, Observer { state ->
            when (state) {
                is SplitTunnelingAppsViewModelHelper.ViewState.Loading ->
                    binding.progress.isVisible = true
                is SplitTunnelingAppsViewModelHelper.ViewState.Content -> {
                    binding.progress.isVisible = false
                    updateLists(
                        itemsAdapter,
                        state,
                        actionAdd,
                        actionRemove,
                        linearLayoutManager
                    )
                    previousSystemAppsState = state.availableSystemApps
                }
            }
        })
    }

    private fun updateLists(
        adapter: GroupAdapter<GroupieViewHolder>,
        content: SplitTunnelingAppsViewModelHelper.ViewState.Content,
        actionAdd: LabeledItemAction,
        actionRemove: LabeledItemAction,
        layoutManager: LinearLayoutManager
    ) {
        val selectedItems = content.selectedApps
        val availableRegularItems = content.availableRegularApps
        val availableSystemItems = content.availableSystemApps
        val headerLabel =
            if (mode == SplitTunnelingMode.INCLUDE_ONLY) R.string.settingsIncludedAppsSelectedHeader
            else R.string.settingsExcludedAppsSelectedHeader
        val headerSelected = getString(headerLabel, selectedItems.size)
        val headerAvailableRegular =
            getString(R.string.settingsSplitTunnelingAvailableRegularHeader, availableRegularItems.size)
        val headerAvailableSystem =
            getString(R.string.settingsSplitTunnelingAvailableSystemHeader, availableSystemItems.appCount())
        // Not using Section.setPlaceholder for empty state because the Section is recreated
        // each time anyway.
        val selectedViewHolders = if (selectedItems.isEmpty()) {
            val emptyText =
                if (mode == SplitTunnelingMode.EXCLUDE_ONLY) R.string.settingsExcludedAppsEmpty
                else R.string.settingsIncludedAppsEmpty
            listOf(EmptyStateItem(emptyText))
        } else {
            selectedItems.map { LabeledItemActionViewHolder(it, CoreR.drawable.ic_proton_cross, actionRemove) }
        }
        val availableRegularViewHolders = availableRegularItems.map {
            LabeledItemActionViewHolder(it, CoreR.drawable.ic_proton_plus, actionAdd)
        }
        val availableSystemViewHolders = when (availableSystemItems) {
            is SplitTunnelingAppsViewModelHelper.SystemAppsState.NotLoaded ->
                listOf(LoadSystemAppsItem(this::loadSystemApps))
            is SplitTunnelingAppsViewModelHelper.SystemAppsState.Loading ->
                listOf(LoadSystemAppsSpinnerItem())
            is SplitTunnelingAppsViewModelHelper.SystemAppsState.Content -> {
                availableSystemItems.apps.map {
                    LabeledItemActionViewHolder(it, CoreR.drawable.ic_proton_plus, actionAdd)
                }
            }
        }
        // Update both sections at once for move animations.
        val systemAppsSection =
            Section(HeaderViewHolder(itemId = 3, text = headerAvailableSystem), availableSystemViewHolders)
        val sections = listOf(
            Section(HeaderViewHolder(itemId = 1, text = headerSelected), selectedViewHolders),
            Section(HeaderViewHolder(itemId = 2, text = headerAvailableRegular), availableRegularViewHolders),
            systemAppsSection
        )
        val onAsyncFinished =
            if (previousSystemAppsState is SplitTunnelingAppsViewModelHelper.SystemAppsState.Loading &&
                availableSystemItems is SplitTunnelingAppsViewModelHelper.SystemAppsState.Content
            ) {
                OnAsyncUpdateListener {
                    // This only works if there were no changes to the list in the meantime, but that's ok.
                    val headerPosition = adapter.getAdapterPosition(systemAppsSection.getItem(0))
                    if (headerPosition >= 0)
                        layoutManager.scrollToPositionWithOffset(headerPosition, 0)
                }
            } else {
                null
            }
        adapter.updateAsync(sections, onAsyncFinished)
    }

    private fun loadSystemApps() {
        viewModel.toggleLoadSystemApps()
    }

    private fun SplitTunnelingAppsViewModelHelper.SystemAppsState.appCount(): Int = when (this) {
        is SplitTunnelingAppsViewModelHelper.SystemAppsState.NotLoaded -> packageNames.size
        is SplitTunnelingAppsViewModelHelper.SystemAppsState.Loading -> packageNames.size
        is SplitTunnelingAppsViewModelHelper.SystemAppsState.Content -> apps.size
    }

    private class EmptyStateItem(
        @StringRes private val textRes: Int
    ) : BindableItemEx<ItemSplitTunnelAppsEmptyBinding>() {
        override fun initializeViewBinding(view: View) = ItemSplitTunnelAppsEmptyBinding.bind(view).apply {
            textLabel.setText(textRes)
        }
        override fun clear() = Unit
        override fun getLayout(): Int = R.layout.item_split_tunnel_apps_empty
        override fun getId(): Long = 1L // There's at most 1 such element in the list.

    }

    private class LoadSystemAppsSpinnerItem : Item<GroupieViewHolder>() {
        override fun bind(viewHolder: GroupieViewHolder, position: Int) = Unit
        override fun getLayout(): Int = R.layout.item_split_tunnel_apps_spinner
        override fun getId(): Long = 1L // There's at most 1 such element in the list.
    }

    private class LoadSystemAppsItem(
        private val onLoad: () -> Unit
    ) : BindableItem<ItemSplitTunnelAppsLoadSystemAppsBinding>() {
        override fun bind(viewBinding: ItemSplitTunnelAppsLoadSystemAppsBinding, position: Int) {
            viewBinding.buttonLoadSystemApps.setOnClickListener { onLoad() }
        }

        override fun getLayout(): Int = R.layout.item_split_tunnel_apps_load_system_apps

        override fun initializeViewBinding(view: View) = ItemSplitTunnelAppsLoadSystemAppsBinding.bind(view)

        override fun getId(): Long = 1L // There's at most 1 such element in the list.
    }

    companion object {
        const val SPLIT_TUNNELING_MODE_KEY = "split tunneling mode"

        fun createContract() = createContract<SplitTunnelingMode>(SettingsSplitTunnelAppsActivity::class) { mode ->
            putExtra(SPLIT_TUNNELING_MODE_KEY, mode)
        }
    }
}
