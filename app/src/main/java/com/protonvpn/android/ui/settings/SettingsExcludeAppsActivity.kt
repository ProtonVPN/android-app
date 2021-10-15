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
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.lifecycle.asLiveData
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.protonvpn.android.R
import com.protonvpn.android.databinding.ActivityRecyclerWithToolbarBinding
import com.protonvpn.android.ui.HeaderViewHolder
import com.protonvpn.android.ui.SaveableSettingsActivity
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.GroupieViewHolder
import com.xwray.groupie.Item
import com.xwray.groupie.Section
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsExcludeAppsActivity : SaveableSettingsActivity<SettingsExcludeAppsViewModel>() {

    override val viewModel: SettingsExcludeAppsViewModel by viewModels()

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

        with(binding.recyclerItems) {
            adapter = itemsAdapter
            layoutManager = LinearLayoutManager(this@SettingsExcludeAppsActivity)
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
                        state.selectedApps,
                        state.availableApps,
                        actionAdd,
                        actionRemove
                    )
                }
            }
        })
    }

    private fun updateLists(
        adapter: GroupAdapter<GroupieViewHolder>,
        selectedItems: List<LabeledItem>,
        availableItems: List<LabeledItem>,
        actionAdd: LabeledItemAction,
        actionRemove: LabeledItemAction
    ) {
        val headerSelected =
            getString(R.string.settingsExcludedAppsSelectedHeader, selectedItems.size)
        val headerAvailable =
            getString(R.string.settingsExcludedAppsAvailableHeader, availableItems.size)
        // Not using Section.setPlaceholder for empty state because the Section is recreated
        // each time anyway.
        val selectedViewHolders = if (selectedItems.isEmpty()) {
            listOf(EmptyStateItem())
        } else {
            selectedItems.map { LabeledItemActionViewHolder(it, R.drawable.ic_clear, actionRemove) }
        }
        val availableViewHolders = availableItems.map {
            LabeledItemActionViewHolder(it, R.drawable.ic_plus, actionAdd)
        }
        // Update both sections at once for move animations.
        val sections = listOf(
            Section(HeaderViewHolder(itemId = 1, text = headerSelected), selectedViewHolders),
            Section(HeaderViewHolder(itemId = 2, text = headerAvailable), availableViewHolders)
        )
        adapter.updateAsync(sections)
    }

    private class EmptyStateItem : Item<GroupieViewHolder>() {
        override fun bind(viewHolder: GroupieViewHolder, position: Int) {
        }

        override fun getLayout(): Int = R.layout.item_excluded_apps_empty
    }

    companion object {
        fun createContract() = createContract(SettingsExcludeAppsActivity::class)
    }
}
