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
package com.protonvpn.test.shared;

import com.protonvpn.android.BuildConfig;
import com.protonvpn.android.models.login.VPNInfo;
import com.protonvpn.android.models.login.VpnInfoResponse;

public final class TestUser {

    public String email;
    public String password;
    public String openVpnPassword;
    public String planName;
    public int maxTier;
    public int maxConnect;

    private TestUser(String email, String pass, String openVpnPassword, String planName, int maxTier,
                     int maxConnect) {
        this.email = email;
        this.password = pass;
        this.openVpnPassword = openVpnPassword;
        this.planName = planName;
        this.maxTier = maxTier;
        this.maxConnect = maxConnect;
    }

    public static TestUser getFreeUser() {
        return new TestUser("Testas1", BuildConfig.TEST_ACCOUNT_PASSWORD, "testas", "free", 0, 1);
    }

    public static TestUser getBasicUser() {
        return new TestUser("Testas2", BuildConfig.TEST_ACCOUNT_PASSWORD, "testas2", "vpnbasic", 1, 2);
    }

    public static TestUser getPlusUser() {
        return new TestUser("Testas3", BuildConfig.TEST_ACCOUNT_PASSWORD, "test", "vpnplus", 2, 5);
    }

    public static TestUser getBadUser() {
        return new TestUser("Testas3", "r4nd0m", "rand", "vpnplus", 2, 5);
    }

    public static TestUser getTrialUser() {
        return new TestUser("Testas5", BuildConfig.TEST_ACCOUNT_PASSWORD, "test", "trial", 1, 2);
    }

    public static TestUser setTrialUserAsExpired() {
        return new TestUser("Testas5", BuildConfig.TEST_ACCOUNT_PASSWORD, "test", "free", 1, 2);
    }

    public VpnInfoResponse getVpnInfoResponse() {
        VPNInfo info = new VPNInfo(1, 0, this.planName, this.maxTier, this.maxConnect, this.email, "16",
            this.openVpnPassword);

        return new VpnInfoResponse(1000, info, 4, 4, 0);
    }
}
