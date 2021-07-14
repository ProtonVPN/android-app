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

package com.protonvpn.android.ui.drawer

import android.os.Bundle
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseActivityV2
import com.protonvpn.android.components.ContentLayout
import com.protonvpn.android.databinding.ActivityRecyclerWithToolbarBinding
import com.protonvpn.android.ui.HeaderViewHolder
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.GroupieViewHolder
import com.xwray.groupie.Item
import com.xwray.groupie.Section
import javax.inject.Inject

@ContentLayout(R.layout.activity_recycler_with_toolbar)
class SettingsExcludeAppsActivity :
    BaseActivityV2<ActivityRecyclerWithToolbarBinding, SettingsExcludeAppsViewModel>() {

    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory

    override fun initViewModel() {
        viewModel =
            ViewModelProvider(this, viewModelFactory).get(SettingsExcludeAppsViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        val addAction = { item: LabeledItem -> viewModel.addAppToExcluded(item) }
        val removeAction = { item: LabeledItem -> viewModel.removeAppFromExcluded(item) }
        viewModel.viewState.asLiveData().observe(this, Observer { state ->
            val headerSelected =
                getString(R.string.settingsExcludedAppsSelectedHeader, state.selectedApps.size)
            val headerAvailable =
                getString(R.string.settingsExcludedAppsAvailableHeader, state.availableApps.size)
            // Not using Section.setPlaceholder for empty state because the Section is recreated
            // each time anyway.
            val selectedItems = if (state.selectedApps.isEmpty()) {
                listOf(EmptyStateItem())
            } else {
                state.selectedApps.map {
                    LabeledItemActionViewHolder(it, R.drawable.ic_clear, removeAction)
                }
            }
            val availableItems = state.availableApps.map {
                LabeledItemActionViewHolder(it, R.drawable.ic_plus, addAction)
            }
            // Update both sections at once for move animations.
            val sections = listOf(
                Section(HeaderViewHolder(itemId = 1, text = headerSelected), selectedItems),
                Section(HeaderViewHolder(itemId = 2, text = headerAvailable), availableItems)
            )
            itemsAdapter.updateAsync(sections)
        })
    }

    private class EmptyStateItem : Item<GroupieViewHolder>() {
        override fun bind(viewHolder: GroupieViewHolder, position: Int) {
        }

        override fun getLayout(): Int = R.layout.item_excluded_apps_empty
    }
}
