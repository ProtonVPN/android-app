/*
 * Copyright (c) 2018 Proton Technologies AG
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
package com.protonvpn.android.ui.splittunneling;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.Theme;
import com.protonvpn.android.R;
import com.protonvpn.android.models.config.UserData;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class IpAdapter extends RecyclerView.Adapter<IpViewHolder> {

    private List<String> data;
    private UserData userData;

    IpAdapter(UserData userData) {
        this.userData = userData;
        data = userData.getSplitTunnelIpAddresses();
    }

    public void dataChanged() {
        data = userData.getSplitTunnelIpAddresses();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public IpViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new IpViewHolder(
            LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false)) {
            @Override
            public void onItemRemoved(String ip) {
                userData.removeIpFromSplitTunnel(ip);
                dataChanged();
            }
        };
    }

    @Override
    public void onBindViewHolder(@NonNull IpViewHolder holder, int position) {
        holder.bindData(data.get(position));
    }

    @Override
    public long getItemId(int position) {
        return data.get(position).hashCode();
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

}

abstract class IpViewHolder extends RecyclerView.ViewHolder {

    @BindView(R.id.textName) TextView name;
    @BindView(R.id.imageIcon) ImageView appIcon;
    @BindView(R.id.clearIcon) ImageView clearIcon;
    @BindView(R.id.textAdd) TextView textAdd;
    private String ip;
    private Context context;

    public abstract void onItemRemoved(String ip);

    IpViewHolder(View view) {
        super(view);
        context = view.getContext();
        ButterKnife.bind(this, view);
        appIcon.setVisibility(View.GONE);
        textAdd.setVisibility(View.GONE);

    }

    @OnClick(R.id.layoutAddRemove)
    public void layoutAddRemove() {
        new MaterialDialog.Builder(context).theme(Theme.DARK)
            .title(R.string.warning)
            .content(R.string.removeExcludedIpDialogDescription)
            .positiveText(R.string.yes)
            .onPositive((dialog, which) -> onItemRemoved(ip))
            .negativeText(R.string.cancel)
            .show();

    }

    public void bindData(String object) {
        ip = object;
        name.setText(object);
    }
}