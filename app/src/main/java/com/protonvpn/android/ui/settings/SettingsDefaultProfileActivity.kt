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

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseActivityV2
import com.protonvpn.android.databinding.ActivityRecyclerWithToolbarBinding
import com.protonvpn.android.databinding.ItemProfileSelectionBinding
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.ui.HeaderViewHolder
import com.protonvpn.android.utils.ServerManager
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.GroupieViewHolder
import com.xwray.groupie.Section
import com.xwray.groupie.viewbinding.BindableItem
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SettingsDefaultProfileActivity : BaseActivityV2() {

    @Inject lateinit var userData: UserData
    @Inject lateinit var serverManager: ServerManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityRecyclerWithToolbarBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initToolbarWithUpEnabled(binding.contentAppbar.toolbar)

        val selectedProfile = serverManager.defaultConnection
        val (prebakedProfiles, customProfiles) = serverManager.getSavedProfiles()
            .partition { it.isPreBakedProfile }
            .toList()
            .map { profiles ->
                profiles.map { ProfileViewHolder(it, it == selectedProfile) }
            }

        val groupAdapter = GroupAdapter<GroupieViewHolder>()
        val prebakedProfilesSection =
            Section(HeaderViewHolder(R.string.recommendedProfilesHeader), prebakedProfiles)
        val customProfilesSection = Section(HeaderViewHolder(R.string.yourProfilesHeader), customProfiles)
        customProfilesSection.setHideWhenEmpty(true)
        groupAdapter.add(prebakedProfilesSection)
        groupAdapter.add(customProfilesSection)

        groupAdapter.setOnItemClickListener { item, _ ->
            if (item is ProfileViewHolder) {
                userData.defaultProfileId = item.profile.id
                finish()
            }
        }

        with(binding.recyclerItems) {
            adapter = groupAdapter
            layoutManager = LinearLayoutManager(context)
        }
    }

    private data class ProfileViewHolder(
        val profile: Profile,
        private val isSelected: Boolean
    ) : BindableItem<ItemProfileSelectionBinding>() {
        override fun bind(viewBinding: ItemProfileSelectionBinding, position: Int) {
            with(viewBinding) {
                radioProfile.text = profile.getDisplayName(root.context)
                radioProfile.isChecked = isSelected

                val iconRes = profile.profileSpecialIcon ?: R.drawable.ic_profile_custom
                radioProfile.setCompoundDrawablesRelativeWithIntrinsicBounds(iconRes, 0, 0, 0)
                val compoundTintList = if (profile.profileColor != null) {
                    val color = ContextCompat.getColor(root.context, profile.profileColor.colorRes)
                    ColorStateList.valueOf(color)
                } else {
                    null
                }
                TextViewCompat.setCompoundDrawableTintList(radioProfile, compoundTintList)
            }
        }

        override fun getId(): Long = profile.id.hashCode().toLong()
        override fun getLayout(): Int = R.layout.item_profile_selection
        override fun initializeViewBinding(view: View) = ItemProfileSelectionBinding.bind(view)
    }
}
