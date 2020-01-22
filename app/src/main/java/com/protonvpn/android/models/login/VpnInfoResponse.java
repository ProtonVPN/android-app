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
package com.protonvpn.android.models.login;

import android.content.Context;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.protonvpn.android.R;

import org.joda.time.Days;
import org.joda.time.Period;

import java.io.Serializable;

public class VpnInfoResponse implements Serializable {

    private VPNInfo vpnInfo;
    private final String accountType;
    private final int subscribed;
    private final int delinquent;

    public VpnInfoResponse(@JsonProperty(value = "Code", required = true) int code,
                           @JsonProperty(value = "VPN", required = true) VPNInfo modulus,
                           @JsonProperty(value = "Subscribed", required = true) int subscribed,
                           @JsonProperty(value = "Services", required = true) int accountType,
                           @JsonProperty(value = "Delinquent", required = true) int delinquent) {
        this.vpnInfo = modulus;
        this.subscribed = subscribed;
        this.delinquent = delinquent;
        this.accountType = accountType == 4 ? "ProtonVPN Account" : "ProtonMail Account";
    }

    public String getPassword() {
        return vpnInfo.getPassword();
    }

    public int getMaxSessionCount() {
        return vpnInfo.getMaxConnect();
    }

    public String getVpnUserName() {
        return vpnInfo.getName();
    }

    public boolean isUserDelinquent() {
        return delinquent >= 3;
    }

    public boolean hasAccessToTier(int serverTier) {
        return vpnInfo.getMaxTier() >= serverTier;
    }

    public String getUserTierName() {
        return vpnInfo.getTierName();
    }

    public int getUserTier() {
        return vpnInfo.getMaxTier();
    }

    public boolean hasInitedTime() {
        return vpnInfo.isRemainingTimeAccessible() && vpnInfo.getTrialRemainingTime() != null;
    }

    public Period getTrialRemainingTime() {
        return vpnInfo.getTrialRemainingTime();
    }

    public String getTrialRemainingTimeString(Context context) {
        Period period = vpnInfo.isRemainingTimeAccessible() ?
                vpnInfo.getTrialRemainingTime() :
                Days.days(7).toPeriod();
        return context.getString(R.string.trialRemainingTimeString,
                period.getDays(), period.getHours(), period.getMinutes(), period.getSeconds());
    }

    public String getAccountType() {
        return accountType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof VpnInfoResponse)) {
            return false;
        }

        VpnInfoResponse that = (VpnInfoResponse) o;

        return (vpnInfo != null ? vpnInfo.equals(that.vpnInfo) : that.vpnInfo == null) && accountType.equals(
            that.accountType);
    }

    @Override
    public int hashCode() {
        int result = vpnInfo != null ? vpnInfo.hashCode() : 0;
        result = 31 * result + accountType.hashCode();
        return result;
    }
}