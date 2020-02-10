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

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;

import com.daimajia.androidanimations.library.Techniques;
import com.daimajia.androidanimations.library.YoYo;
import com.protonvpn.android.R;
import com.protonvpn.android.bus.ConnectToProfile;
import com.protonvpn.android.bus.EventBus;
import com.protonvpn.android.bus.ServerSelected;
import com.protonvpn.android.components.BaseViewHolder;
import com.protonvpn.android.components.SecureCoreCallback;
import com.protonvpn.android.components.TriangledTextView;
import com.protonvpn.android.models.config.UserData;
import com.protonvpn.android.models.profiles.Profile;
import com.protonvpn.android.models.vpn.Server;
import com.protonvpn.android.utils.EventBusBinder;
import com.protonvpn.android.utils.ServerManager;
import com.protonvpn.android.vpn.VpnStateMonitor;
import com.squareup.otto.Subscribe;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import butterknife.BindView;
import butterknife.OnClick;

public class ProfilesAdapter extends RecyclerView.Adapter<ProfilesAdapter.ServersViewHolder> {

    private List<Profile> profileList;
    private ProfilesFragment profilesFragment;
    private ServerManager manager;
    private UserData userData;
    private VpnStateMonitor stateMonitor;

    ProfilesAdapter(ProfilesFragment fragment) {
        super();
        profilesFragment = fragment;
        manager = fragment.manager;
        userData = fragment.userData;
        stateMonitor = fragment.vpnStateMonitor;
        profileList = manager.getSavedProfiles();
    }

    @NonNull
    @Override
    public ServersViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ProfilesAdapter.ServersViewHolder(
            LayoutInflater.from(parent.getContext()).inflate(R.layout.item_profile, parent, false));
    }

    @Override
    public void onBindViewHolder(ServersViewHolder holder, int position) {
        holder.bindData(profileList.get(position));
    }

    @Override
    public void onViewRecycled(@NonNull ServersViewHolder holder) {
        holder.unbind();
    }

    @Override
    public int getItemCount() {
        return profileList.size();
    }

    public class ServersViewHolder extends BaseViewHolder<Profile> implements View.OnClickListener,
        SecureCoreCallback {

        @BindView(R.id.textServer) TextView textServer;
        @BindView(R.id.radioServer) RadioButton radioServer;
        @BindView(R.id.buttonConnect) TriangledTextView buttonConnect;
        @BindView(R.id.textConnected) TextView textConnected;
        @BindView(R.id.textServerNotSet) TextView textServerNotSet;
        @BindView(R.id.imageCountry) ImageView imageCountry;
        @BindView(R.id.layoutProfileColor) View layoutProfileColor;
        @BindView(R.id.imageEdit) ImageView imageEdit;
        private Profile profile;
        private Server server;

        private EventBusBinder eventBusBinder = new EventBusBinder(this);

        @OnClick(R.id.buttonConnect)
        public void buttonConnect() {
            if (server != null) {
                EventBus.post(new ConnectToProfile(profile));
            }
        }

        ServersViewHolder(View view) {
            super(view);
            view.setOnClickListener(this);
        }

        @Override
        public void bindData(Profile object) {
            this.profile = object;
            this.server = object.getServer();
            textServer.setText(object.getName());
            radioServer.setChecked(false);
            radioServer.setClickable(false);

            buttonConnect.setAlpha(0);
            buttonConnect.setClickable(false);
            buttonConnect.setText(userData.hasAccessToServer(server) ? R.string.connect : R.string.upgrade);
            initConnectedStatus();
            textServerNotSet.setVisibility(server != null ? View.GONE : View.VISIBLE);
            imageEdit.setVisibility(profile.isPreBakedProfile() ? View.INVISIBLE : View.VISIBLE);
            layoutProfileColor.setBackgroundColor(Color.parseColor(object.getColor()));

            eventBusBinder.register();
        }

        public void unbind() {
            eventBusBinder.unregister();
        }

        private void initConnectedStatus() {
            boolean connectedToServer = server != null && stateMonitor.isConnectedTo(server);
            textConnected.setVisibility(connectedToServer ? View.VISIBLE : View.GONE);
            radioServer.setChecked(stateMonitor.isConnectedTo(server));
        }

        @Subscribe
        public void onServerSelected(ServerSelected data) {
            if (radioServer.isChecked() && !data.isSameSelection(profile, server)) {
                markAsSelected(false);
                initConnectedStatus();
            }
        }

        private void markAsSelected(boolean enable) {
            buttonConnect.setClickable(enable);
            if (textConnected.getVisibility() != View.VISIBLE) {
                radioServer.setChecked(enable);
                showConnectButton(enable);
            }
        }

        private void showConnectButton(boolean show) {
            YoYo.with(show ? Techniques.SlideInRight : Techniques.SlideOutRight)
                .duration(300)
                .playOn(buttonConnect);
        }

        @OnClick(R.id.imageEdit)
        public void imageEdit() {
            ProfileActivity.Companion.navigateForEdit(profilesFragment, profile);
        }

        @Override
        public void onClick(View v) {
            if (!stateMonitor.isConnectedTo(server) && server != null) {
                markAsSelected(!radioServer.isChecked());
                EventBus.post(new ServerSelected(profile, server));
            }
        }

        @Override
        public void onConnect(Profile profile) {
            EventBus.post(new ConnectToProfile(profile));
            showConnectButton(false);
            initConnectedStatus();
            EventBus.post(new ServerSelected(profile, profile.getServer()));
        }
    }
}
