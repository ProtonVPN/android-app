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
package com.protonvpn.android.ui.drawer;

import android.os.Bundle;
import android.widget.TextView;

import com.protonvpn.android.BuildConfig;
import com.protonvpn.android.R;
import com.protonvpn.android.components.BaseActivity;
import com.protonvpn.android.components.ContentLayout;
import com.protonvpn.android.models.config.UserData;
import com.protonvpn.android.utils.HtmlTools;

import javax.inject.Inject;

import androidx.annotation.StringRes;
import butterknife.BindView;
import butterknife.OnClick;

import static com.protonvpn.android.utils.AndroidUtilsKt.openProtonUrl;

@ContentLayout(R.layout.activity_account)
public class AccountActivity extends BaseActivity {

    @BindView(R.id.textUser) TextView textUser;
    @BindView(R.id.textAccountTier) TextView textAccountTier;
    @BindView(R.id.textAccountType) TextView textAccountType;
    @BindView(R.id.textVersion) TextView textVersion;
    @Inject UserData userData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initToolbarWithUpEnabled();
        initContent();
    }

    private void initContent() {
        textAccountTier.setText(userData.getVpnInfoResponse() != null ?
            getText(getAccountTypeNaming(userData.getVpnInfoResponse().getUserTierName())) : "development");
        textUser.setText(
            userData.getVpnInfoResponse() != null ? userData.getUser() : "development@protonvpn.com");
        textAccountType.setText(HtmlTools.fromHtml(
            userData.getVpnInfoResponse() != null ? userData.getVpnInfoResponse().getAccountType() :
                "Debug"));
        textVersion.setText(getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME);
    }

    @StringRes
    private int getAccountTypeNaming(String accountType) {
        switch (accountType) {
            case "trial":
                return R.string.accountTrial;
            case "free":
                return R.string.accountFree;
            case "vpnbasic":
                return R.string.accountBasic;
            case "vpnplus":
                return R.string.accountPlus;
            case "visionary":
                return R.string.accountVisionary;
        }
        return R.string.accountFree;
    }

    @OnClick(R.id.buttonManageAccount)
    public void drawerButtonAccount() {
        openProtonUrl(this, "https://account.protonvpn.com/login");
    }

}
