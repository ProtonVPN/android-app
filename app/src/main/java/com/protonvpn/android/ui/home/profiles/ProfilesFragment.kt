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

import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import butterknife.BindView
import butterknife.OnClick
import com.protonvpn.android.R
import com.protonvpn.android.bus.OnProfilesChanged
import com.protonvpn.android.bus.VpnStateChanged
import com.protonvpn.android.components.BaseFragment
import com.protonvpn.android.components.ContentLayout
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.ui.home.profiles.ProfileActivity.Companion.navigateForCreation
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.vpn.VpnStateMonitor
import com.squareup.otto.Subscribe
import javax.inject.Inject

@ContentLayout(R.layout.fragment_profiles)
class ProfilesFragment : BaseFragment() {

    @BindView(R.id.list) lateinit var list: RecyclerView
    @Inject lateinit var manager: ServerManager
    @Inject lateinit var userData: UserData
    @Inject lateinit var vpnStateMonitor: VpnStateMonitor

    private var adapter: ProfilesAdapter? = null

    @OnClick(R.id.layoutCreateNew)
    fun layoutCreateNew() {
        navigateForCreation(this)
    }

    override fun onViewCreated() {
        adapter = ProfilesAdapter(this, viewLifecycleOwner.lifecycleScope)
        list.adapter = adapter
        registerForEvents()
    }

    override fun onDestroyView() { // Force recycling of view holders to enable cleanup
        list.adapter = null
        adapter = null
        super.onDestroyView()
    }

    @Subscribe
    fun onProfilesUpdated(instance: OnProfilesChanged?) {
        adapter?.notifyDataSetChanged()
    }

    @Subscribe
    fun onVpnStateChange(change: VpnStateChanged?) {
        adapter?.notifyDataSetChanged()
    }
}
