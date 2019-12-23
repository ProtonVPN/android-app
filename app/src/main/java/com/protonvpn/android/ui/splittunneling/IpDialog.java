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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.text.Editable;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.protonvpn.android.R;
import com.protonvpn.android.components.BaseDialog;
import com.protonvpn.android.components.CompressedTextWatcher;
import com.protonvpn.android.components.ContentLayout;
import com.protonvpn.android.models.config.UserData;

import org.apache.http.conn.util.InetAddressUtils;

import javax.inject.Inject;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import butterknife.BindView;
import butterknife.OnClick;

@ContentLayout(R.layout.dialog_split_tunnel)
public class IpDialog extends BaseDialog {

    private IpAdapter adapter;
    @BindView(R.id.textTitle) TextView textTitle;
    @BindView(R.id.textDescription) TextView textDescription;
    @BindView(R.id.list) RecyclerView list;
    @BindView(R.id.editIP) EditText editIP;
    @BindView(R.id.progressBar) ProgressBar progressBar;
    @BindView(R.id.textAdd) TextView textAdd;
    @BindView(R.id.textEmpty) TextView textEmpty;
    @BindView(R.id.constraintLayout) View constraintLayout;

    @Inject UserData userData;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onViewCreated() {
        list.setLayoutManager(new LinearLayoutManager(getActivity()));
        adapter = new IpAdapter(userData);
        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                checkForEmptyList();
            }
        });
        list.setAdapter(adapter);
        textTitle.setText("Exclude IP Addresses");
        textDescription.setText("Add IP addresses you want to exclude from the VPN traffic.");
        editIP.setContentDescription("Add IP Address");
        editIP.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.GONE);
        checkForEmptyList();
        editIP.addTextChangedListener(new CompressedTextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                textAdd.setVisibility(
                    InetAddressUtils.isIPv4Address(editable.toString()) ? View.VISIBLE : View.GONE);
            }
        });
        constraintLayout.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                hideKeyboard(getActivity());
            }
        });
        list.setOnTouchListener((view, motionEvent) -> {
            hideKeyboard(getActivity());
            return false;
        });
    }

    public void hideKeyboard(Activity activity) {
        if (activity != null && activity.getWindow() != null) {
            InputMethodManager imm =
                (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(editIP.getWindowToken(), 0);
        }
    }

    private void checkForEmptyList() {
        boolean isEmpty = userData.getSplitTunnelIpAddresses().isEmpty();
        textEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
    }

    @OnClick(R.id.textDone)
    public void textDone() {
        dismiss();
    }

    @OnClick(R.id.textAdd)
    public void textAdd() {
        if (userData.getSplitTunnelIpAddresses().contains(editIP.getText().toString())) {
            Toast.makeText(getActivity(), "Already excluded", Toast.LENGTH_LONG).show();
        }
        else {
            userData.addIpToSplitTunnel(editIP.getText().toString());
            editIP.setText("");
            adapter.dataChanged();
            hideKeyboard(getActivity());
        }
    }

}