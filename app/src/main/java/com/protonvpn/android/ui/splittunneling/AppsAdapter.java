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

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.protonvpn.android.R;
import com.protonvpn.android.models.config.UserData;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

public class AppsAdapter extends RecyclerView.Adapter<AppsViewHolder> {

    private List<SelectedApplicationEntry> data = new ArrayList<>();
    private UserData userData;

    public void setData(List<SelectedApplicationEntry> data) {
        if (data != null) {
            this.data.addAll(data);
        }
        notifyDataSetChanged();
    }

    public AppsAdapter(UserData userData) {
        this.userData = userData;
    }

    @NonNull
    @Override
    public AppsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new AppsViewHolder(
            LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false)) {
            @Override
            public void removeApp(String packageName) {
                userData.removeAppFromSplitTunnel(packageName);
            }

            @Override
            public void addApp(String packageName) {
                userData.addAppToSplitTunnel(packageName);
            }
        };
    }

    @Override
    public void onBindViewHolder(@NonNull AppsViewHolder holder, int position) {
        holder.bindData(data.get(position));
    }

    @Override
    public long getItemId(int position) {
        return data.get(position).toString().hashCode();
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

}

abstract class AppsViewHolder extends RecyclerView.ViewHolder {

    @BindView(R.id.textName) TextView textName;
    @BindView(R.id.imageIcon) ImageView imageIcon;
    @BindView(R.id.clearIcon) ImageView clearIcon;
    @BindView(R.id.textAdd) TextView textAdd;
    private SelectedApplicationEntry item;

    public AppsViewHolder(View view) {
        super(view);
        ButterKnife.bind(this, view);
    }

    public abstract void removeApp(String packageName);

    public abstract void addApp(String packageName);

    @OnClick(R.id.layoutAddRemove)
    public void layoutAddRemove() {
        item.setSelected(!item.isSelected());
        if (item.isSelected()) {
            addApp(item.getInfo().packageName);
        }
        else {
            removeApp(item.getInfo().packageName);
        }
        initSelection();
    }

    public void bindData(SelectedApplicationEntry object) {
        this.item = object;
        initSelection();
        imageIcon.setImageDrawable(object.getIcon());
        textName.setText(object.toString());
    }

    private void initSelection() {
        clearIcon.setVisibility(item.isSelected() ? VISIBLE : GONE);
        textAdd.setVisibility(item.isSelected() ? GONE : VISIBLE);
    }

}
