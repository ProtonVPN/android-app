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

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseFragmentV2
import com.protonvpn.android.components.ContentLayout
import com.protonvpn.android.databinding.FragmentProfilesBinding
import com.protonvpn.android.ui.home.profiles.ProfileActivity.Companion.navigateForCreation
import javax.inject.Inject

@ContentLayout(R.layout.fragment_profiles)
class ProfilesFragment : BaseFragmentV2<ProfilesViewModel, FragmentProfilesBinding>() {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    override fun initViewModel() {
        viewModel =
                ViewModelProviders.of(this, viewModelFactory).get(ProfilesViewModel::class.java)
    }

    override fun onViewCreated() {
        val adapter = ProfilesAdapter(this, viewModel, viewLifecycleOwner)

        with(binding) {
            list.adapter = adapter
            layoutCreateNew.setOnClickListener {
                navigateForCreation(this@ProfilesFragment)
            }
        }

        viewModel.profilesUpdateEvent.observe(viewLifecycleOwner) {
            adapter.notifyDataSetChanged()
        }
    }

    override fun onDestroyView() {
        // Force recycling of view holders to enable cleanup
        binding.list.adapter = null
        super.onDestroyView()
    }
}
