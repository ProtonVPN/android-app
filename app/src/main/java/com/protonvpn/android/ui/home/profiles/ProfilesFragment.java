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
package com.protonvpn.android.ui.home.profiles;

import com.protonvpn.android.R;
import com.protonvpn.android.bus.OnProfilesChanged;
import com.protonvpn.android.bus.VpnStateChanged;
import com.protonvpn.android.components.BaseFragment;
import com.protonvpn.android.components.ContentLayout;
import com.protonvpn.android.models.config.UserData;
import com.protonvpn.android.utils.ServerManager;
import com.protonvpn.android.vpn.VpnStateMonitor;
import com.squareup.otto.Subscribe;

import javax.inject.Inject;

import androidx.recyclerview.widget.RecyclerView;
import butterknife.BindView;
import butterknife.OnClick;

@ContentLayout(R.layout.fragment_profiles)
public class ProfilesFragment extends BaseFragment {

    @BindView(R.id.list) RecyclerView list;
    @Inject ServerManager manager;
    @Inject UserData userData;
    @Inject VpnStateMonitor vpnStateMonitor;
    private ProfilesAdapter adapter;

    public static ProfilesFragment newInstance() {
        return new ProfilesFragment();
    }

    @OnClick(R.id.layoutCreateNew)
    public void layoutCreateNew() {
        ProfileActivity.Companion.navigateForCreation(this);
    }

    @Override
    public void onViewCreated() {
        adapter = new ProfilesAdapter(this);
        list.setAdapter(adapter);
        registerForEvents();
    }

    @Override
    public void onDestroyView() {
        // Force recycling of view holders to enable cleanup
        list.setAdapter(null);
        adapter = null;
        super.onDestroyView();
    }

    @Subscribe
    public void onProfilesUpdated(OnProfilesChanged instance) {
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    @Subscribe
    public void onVpnStateChange(VpnStateChanged change) {
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }
}