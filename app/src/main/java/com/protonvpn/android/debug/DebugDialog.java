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
package com.protonvpn.android.debug;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.Theme;
import com.protonvpn.android.components.RetainableDialog;
import com.protonvpn.android.ui.drawer.LogActivity;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentTransaction;

public class DebugDialog extends RetainableDialog {

    public static final String TAG = "Debug-dialog";

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new MaterialDialog.Builder(getContext()).title("Debug only options")
            .theme(Theme.DARK)
            .cancelable(false)
            .negativeText("Show VPN log")
            .onNegative(new MaterialDialog.SingleButtonCallback() {
                @Override
                public void onClick(@NonNull MaterialDialog materialDialog,
                                    @NonNull DialogAction dialogAction) {
                    startActivity(new Intent(getActivity(), LogActivity.class));
                }
            })
            .show();
    }

    public static void showDialog(FragmentActivity activity, String tag) {
        if (!isDialogAdded(activity, tag)) {
            addFragmentToActivity(activity, tag, new DebugDialog());
        }
    }

    private static void addFragmentToActivity(FragmentActivity activity, String tag, DebugDialog fragment) {
        FragmentTransaction ft = activity.getSupportFragmentManager().beginTransaction();
        ft.add(fragment, tag);
        ft.commitAllowingStateLoss();
    }

    public static boolean isDialogAdded(FragmentActivity activity, String tag) {
        Fragment fragment = activity.getSupportFragmentManager().findFragmentByTag(tag);
        return fragment != null;
    }
}