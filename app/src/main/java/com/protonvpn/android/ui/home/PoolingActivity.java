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
package com.protonvpn.android.ui.home;

import android.os.Bundle;

import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.Theme;
import com.protonvpn.android.R;
import com.protonvpn.android.models.config.UserData;
import com.protonvpn.android.models.login.VpnInfoResponse;
import com.protonvpn.android.ui.onboarding.WelcomeDialog;
import com.protonvpn.android.vpn.VpnActivity;

import org.joda.time.DateTime;

import javax.inject.Inject;

public abstract class PoolingActivity extends VpnActivity {

    @Inject UserData userData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if ("trial".equals(userData.getVpnInfoResponse().getUserTierName())
            && !userData.wasTrialDialogRecentlyShowed()) {
            WelcomeDialog.showDialog(getSupportFragmentManager(), WelcomeDialog.DialogType.TRIAL);
            userData.setTrialDialogShownAt(new DateTime());
        }
    }

    public void shouldExpiredDialog(VpnInfoResponse newVpnInfo) {
        WelcomeDialog dialog = (WelcomeDialog) WelcomeDialog.getDialog(getSupportFragmentManager());
        if (dialog != null) {
            dialog.dismissAllowingStateLoss();
        }
        if (newVpnInfo.getUserTierName().equals("free") && userData.getVpnInfoResponse()
            .getUserTierName()
            .equals("trial")) {
            showExpiredDialog();
        }
    }

    public abstract void onTrialEnded();

    public void showExpiredDialog() {
        onTrialEnded();
        new MaterialDialog.Builder(this).theme(Theme.DARK)
            .title("ProtonVPN Plus Trial Expired")
            .content(R.string.freeTrialExpired)
            .positiveText("Upgrade")
            .onPositive((dialog, which) -> openUrl("https://account.protonvpn.com/dashboard"))
            .negativeText(R.string.cancel)
            .show();
    }
}
