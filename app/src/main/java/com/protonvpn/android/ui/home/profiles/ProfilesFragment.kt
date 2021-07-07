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
import android.view.View
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.protonvpn.android.R
import com.protonvpn.android.bus.ConnectToProfile
import com.protonvpn.android.bus.EventBus
import com.protonvpn.android.components.BaseFragmentV2
import com.protonvpn.android.components.ContentLayout
import com.protonvpn.android.databinding.FragmentProfilesBinding
import com.protonvpn.android.databinding.ItemHeaderBinding
import com.protonvpn.android.databinding.ItemProfileListBinding
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.ui.home.profiles.ProfileActivity.Companion.navigateForCreation
import com.protonvpn.android.utils.BindableItemEx
import com.protonvpn.android.utils.getSelectableItemBackgroundRes
import com.protonvpn.android.utils.getThemeColor
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.GroupieViewHolder
import com.xwray.groupie.Section
import com.xwray.groupie.databinding.BindableItem
import javax.inject.Inject

@ContentLayout(R.layout.fragment_profiles)
class ProfilesFragment : BaseFragmentV2<ProfilesViewModel, FragmentProfilesBinding>() {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    override fun initViewModel() {
        viewModel =
                ViewModelProvider(this, viewModelFactory).get(ProfilesViewModel::class.java)
    }

    override fun onViewCreated() {
        val adapter = GroupAdapter<GroupieViewHolder>()
        val prebakedProfilesSection = Section(HeaderViewHolder(R.string.yourProfilesHeader))
        val customProfilesSection = Section(HeaderViewHolder(R.string.recommendedProfilesHeader))
        customProfilesSection.setHideWhenEmpty(true)
        adapter.add(prebakedProfilesSection)
        adapter.add(customProfilesSection)

        with(binding) {
            list.adapter = adapter
            textCreateProfile.setOnClickListener {
                navigateForCreation(this@ProfilesFragment)
            }
            // Profiles don't have IDs so they always animate as remove + add which doesn't look
            // good, let's disable animations.
            list.itemAnimator = null
        }
        val editAction = { profile: Profile -> ProfileActivity.navigateForEdit(this, profile) }
        viewModel.preBakedProfiles.observe(viewLifecycleOwner, Observer {
            prebakedProfilesSection.update(it.map { ProfileViewHolder(it, editAction) })
        })
        viewModel.userCreatedProfiles.observe(viewLifecycleOwner, Observer {
            customProfilesSection.update(it.map { ProfileViewHolder(it, editAction) })
        })
    }

    override fun onDestroyView() {
        // Force recycling of view holders to enable cleanup
        binding.list.adapter = null
        super.onDestroyView()
    }

    private class HeaderViewHolder(
        @StringRes private val text: Int
    ) : BindableItem<ItemHeaderBinding>() {

        override fun bind(viewBinding: ItemHeaderBinding, position: Int) {
            viewBinding.textHeader.setText(text)
        }

        override fun getLayout(): Int = R.layout.item_header
    }

    private class ProfileViewHolder(
        private val item: ProfilesViewModel.ProfileItem,
        private val editAction: (Profile) -> Unit
    ) : BindableItemEx<ItemProfileListBinding>() {

        override fun bind(viewBinding: ItemProfileListBinding, position: Int) = with(viewBinding) {
            super.bind(viewBinding, position)
            val profile = item.profile
            val server = profile.server
            val online = server?.online == true

            textServer.text = profile.getDisplayName(textServer.context)

            val hasAccess = item.hasAccess
            buttonUpgrade.isVisible = !hasAccess && server != null
            buttonConnect.isVisible = hasAccess && online
            val connectColorAttr =
                if (item.isConnected) R.attr.brand_norm else R.attr.proton_interaction_weak
            buttonConnect.backgroundTintList =
                ColorStateList.valueOf(root.getThemeColor(connectColorAttr))

            imageWrench.isVisible = hasAccess && !online
            buttonConnect.contentDescription = textServer.text

            val editClickListener = View.OnClickListener {
                editAction(profile)
            }
            val connectUpgradeClickListener = View.OnClickListener {
                val connectTo = if (item.isConnected) null else profile
                EventBus.post(ConnectToProfile(connectTo))
            }
            buttonConnect.setOnClickListener(connectUpgradeClickListener)
            buttonUpgrade.setOnClickListener(connectUpgradeClickListener)
            buttonUpgrade.contentDescription = textServer.text
            textServerNotSet.isVisible = server == null
            profileEditButton.isVisible = !profile.isPreBakedProfile
            imageIcon.setImageResource(profile.profileSpecialIcon ?: R.drawable.ic_profile_custom)
            imageIcon.imageTintList = if (profile.profileColor != null) {
                val color =
                    ContextCompat.getColor(profileItem.context, profile.profileColor.colorRes)
                ColorStateList.valueOf(color)
            } else {
                null
            }
            profileEditButton.setOnClickListener(editClickListener)
            profileItem.setOnClickListener(if (profile.isPreBakedProfile) null else editClickListener)
            profileItem.setBackgroundResource(
                if (profile.isPreBakedProfile) 0 else profileItem.getSelectableItemBackgroundRes()
            )
        }

        override fun clear() {
        }

        override fun getLayout(): Int = R.layout.item_profile_list
    }
}
