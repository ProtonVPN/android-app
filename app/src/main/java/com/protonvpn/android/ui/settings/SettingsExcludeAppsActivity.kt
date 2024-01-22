/*
 * Copyright (c) 2021. Proton Technologies AG
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
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.lifecycle.asLiveData
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.protonvpn.android.R
import com.protonvpn.android.databinding.ActivityRecyclerWithToolbarBinding
import com.protonvpn.android.databinding.ItemExcludedAppsLoadSystemAppsBinding
import com.protonvpn.android.ui.HeaderViewHolder
import com.protonvpn.android.ui.SaveableSettingsActivity
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.GroupieViewHolder
import com.xwray.groupie.Item
import com.xwray.groupie.OnAsyncUpdateListener
import com.xwray.groupie.Section
import com.xwray.groupie.viewbinding.BindableItem
import dagger.hilt.android.AndroidEntryPoint
import me.proton.core.presentation.R as CoreR

@AndroidEntryPoint
class SettingsExcludeAppsActivity : SaveableSettingsActivity<SettingsExcludeAppsViewModel>() {

    override val viewModel: SettingsExcludeAppsViewModel by viewModels()

    private var previousSystemAppsState: SettingsExcludeAppsViewModel.SystemAppsState? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityRecyclerWithToolbarBinding.inflate(layoutInflater)
        setContentView(binding.root)
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

        val actionAdd = { item: LabeledItem -> viewModel.addAppToExcluded(item) }
        val actionRemove = { item: LabeledItem -> viewModel.removeAppFromExcluded(item) }
        viewModel.viewState.asLiveData().observe(this, Observer { state ->
            when (state) {
                is SettingsExcludeAppsViewModel.ViewState.Loading ->
                    binding.progress.isVisible = true
                is SettingsExcludeAppsViewModel.ViewState.Content -> {
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
        content: SettingsExcludeAppsViewModel.ViewState.Content,
        actionAdd: LabeledItemAction,
        actionRemove: LabeledItemAction,
        layoutManager: LinearLayoutManager
    ) {
        val selectedItems = content.selectedApps
        val availableRegularItems = content.availableRegularApps
        val availableSystemItems = content.availableSystemApps
        val headerSelected =
            getString(R.string.settingsExcludedAppsSelectedHeader, selectedItems.size)
        val headerAvailableRegular =
            getString(R.string.settingsExcludedAppsAvailableRegularHeader, availableRegularItems.size)
        val headerAvailableSystem =
            getString(R.string.settingsExcludedAppsAvailableSystemHeader, availableSystemItems.appCount())
        // Not using Section.setPlaceholder for empty state because the Section is recreated
        // each time anyway.
        val selectedViewHolders = if (selectedItems.isEmpty()) {
            listOf(EmptyStateItem())
        } else {
            selectedItems.map { LabeledItemActionViewHolder(it, CoreR.drawable.ic_proton_cross, actionRemove) }
        }
        val availableRegularViewHolders = availableRegularItems.map {
            LabeledItemActionViewHolder(it, CoreR.drawable.ic_proton_plus, actionAdd)
        }
        val availableSystemViewHolders = when (availableSystemItems) {
            is SettingsExcludeAppsViewModel.SystemAppsState.NotLoaded ->
                listOf(LoadSystemAppsItem(this::loadSystemApps))
            is SettingsExcludeAppsViewModel.SystemAppsState.Loading ->
                listOf(LoadSystemAppsSpinnerItem())
            is SettingsExcludeAppsViewModel.SystemAppsState.Content -> {
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
            if (previousSystemAppsState is SettingsExcludeAppsViewModel.SystemAppsState.Loading &&
                availableSystemItems is SettingsExcludeAppsViewModel.SystemAppsState.Content
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
        viewModel.triggerLoadSystemApps()
    }

    private fun SettingsExcludeAppsViewModel.SystemAppsState.appCount(): Int = when (this) {
        is SettingsExcludeAppsViewModel.SystemAppsState.NotLoaded -> packageNames.size
        is SettingsExcludeAppsViewModel.SystemAppsState.Loading -> packageNames.size
        is SettingsExcludeAppsViewModel.SystemAppsState.Content -> apps.size
    }

    private class EmptyStateItem : Item<GroupieViewHolder>() {
        override fun bind(viewHolder: GroupieViewHolder, position: Int) {
        }

        override fun getLayout(): Int = R.layout.item_excluded_apps_empty

        override fun getId(): Long = 1L // There's at most 1 such element in the list.
    }

    private class LoadSystemAppsSpinnerItem : Item<GroupieViewHolder>() {
        override fun bind(viewHolder: GroupieViewHolder, position: Int) {
        }

        override fun getLayout(): Int = R.layout.item_excluded_apps_spinner

        override fun getId(): Long = 1L // There's at most 1 such element in the list.
    }

    private class LoadSystemAppsItem(
        private val onLoad: () -> Unit
    ) : BindableItem<ItemExcludedAppsLoadSystemAppsBinding>() {
        override fun bind(viewBinding: ItemExcludedAppsLoadSystemAppsBinding, position: Int) {
            viewBinding.buttonLoadSystemApps.setOnClickListener { onLoad() }
        }

        override fun getLayout(): Int = R.layout.item_excluded_apps_load_system_apps

        override fun initializeViewBinding(view: View) = ItemExcludedAppsLoadSystemAppsBinding.bind(view)

        override fun getId(): Long = 1L // There's at most 1 such element in the list.
    }

    companion object {
        fun createContract() = createContract(SettingsExcludeAppsActivity::class)
    }
}
