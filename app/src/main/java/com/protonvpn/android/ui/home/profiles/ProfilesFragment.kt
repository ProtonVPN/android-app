/*
 * Copyright (c) 2017 Proton Technologies AG
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
package com.protonvpn.android.ui.home.profiles

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import com.protonvpn.android.R
import com.protonvpn.android.bus.ConnectToProfile
import com.protonvpn.android.bus.EventBus
import com.protonvpn.android.databinding.FragmentProfilesBinding
import com.protonvpn.android.databinding.ItemProfileListBinding
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.ui.HeaderViewHolder
import com.protonvpn.android.ui.planupgrade.UpgradeDialogActivity
import com.protonvpn.android.ui.planupgrade.UpgradeProfilesHighlightsFragment
import com.protonvpn.android.utils.AndroidUtils.launchActivity
import com.protonvpn.android.utils.BindableItemEx
import com.protonvpn.android.utils.getSelectableItemBackgroundRes
import com.protonvpn.android.vpn.ConnectTrigger
import com.protonvpn.android.vpn.DisconnectTrigger
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.GroupieViewHolder
import com.xwray.groupie.Section
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ProfilesFragment : Fragment(R.layout.fragment_profiles) {

    private val viewModel: ProfilesViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentProfilesBinding.bind(view)
        val adapter = GroupAdapter<GroupieViewHolder>()
        val prebakedProfilesSection = Section(HeaderViewHolder(R.string.recommendedProfilesHeader))
        val customProfilesSection = Section(HeaderViewHolder(R.string.yourProfilesHeader))
        customProfilesSection.setHideWhenEmpty(true)
        adapter.add(prebakedProfilesSection)
        adapter.add(customProfilesSection)

        with(binding) {
            list.adapter = adapter
            textCreateProfile.setOnClickListener {
                lifecycleScope.launch {
                    if (viewModel.canCreateProfile())
                        ProfileEditActivity.navigateForCreation(this@ProfilesFragment)
                    else
                        UpgradeDialogActivity.launch<UpgradeProfilesHighlightsFragment>(it.context)
                }
            }
            // Profiles don't have IDs so they always animate as remove + add which doesn't look
            // good, let's disable animations.
            list.itemAnimator = null
        }
        val editAction = { profile: Profile -> ProfileEditActivity.navigateForEdit(this, profile) }
        viewModel.preBakedProfiles.asLiveData().observe(viewLifecycleOwner) {
            prebakedProfilesSection.update(it.map { ProfileViewHolder(it, editAction) })
        }
        viewModel.userCreatedProfiles.asLiveData().observe(viewLifecycleOwner) {
            customProfilesSection.update(it.map { ProfileViewHolder(it, editAction) })
        }
    }

    private class ProfileViewHolder(
        private val item: ProfilesViewModel.ProfileItem,
        private val editAction: (Profile) -> Unit
    ) : BindableItemEx<ItemProfileListBinding>() {

        override fun bind(viewBinding: ItemProfileListBinding, position: Int) = with(viewBinding) {
            super.bind(viewBinding, position)
            val profile = item.profile
            val server = item.server
            val online = server?.online == true

            textServer.text = profile.getDisplayName(textServer.context)

            val accessType = item.accessType
            buttonUpgrade.isVisible = accessType != ProfilesViewModel.AccessType.Full && server != null
            buttonConnect.isVisible = accessType == ProfilesViewModel.AccessType.Full && online
            buttonConnect.isOn = item.isConnected

            imageWrench.isVisible = accessType == ProfilesViewModel.AccessType.Full && !online
            buttonConnect.contentDescription = textServer.text

            val editClickListener = View.OnClickListener {
                editAction(profile)
            }
            val connectUpgradeClickListener = View.OnClickListener {
                if (item.accessType == ProfilesViewModel.AccessType.Restricted) {
                    UpgradeDialogActivity.launch<UpgradeProfilesHighlightsFragment>(profileItem.context)
                } else {
                    val event = ConnectToProfile(
                        profile.takeUnless { item.isConnected },
                        ConnectTrigger.Profile("profile power button"),
                        DisconnectTrigger.Profile("profile power button")
                    )
                    EventBus.post(event)
                }
            }
            buttonConnect.setOnClickListener(connectUpgradeClickListener)
            buttonUpgrade.setOnClickListener(connectUpgradeClickListener)
            buttonUpgrade.contentDescription = textServer.text
            textServerNotSet.isVisible = server == null
            profileEditButton.isVisible = item.canEdit
            imageIcon.setImageResource(profile.profileSpecialIcon ?: R.drawable.ic_profile_custom)
            imageIcon.imageTintList = if (profile.profileColor != null) {
                val color =
                    ContextCompat.getColor(profileItem.context, profile.profileColor.colorRes)
                ColorStateList.valueOf(color)
            } else {
                null
            }
            profileEditButton.setOnClickListener(editClickListener)
            profileItem.setOnClickListener(if (item.canEdit) editClickListener else null)
            profileItem.setBackgroundResource(
                if (profile.isPreBakedProfile) 0 else profileItem.getSelectableItemBackgroundRes()
            )
        }

        override fun clear() {
        }

        override fun getLayout(): Int = R.layout.item_profile_list

        override fun initializeViewBinding(view: View) = ItemProfileListBinding.bind(view)
    }
}
