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
package com.protonvpn.android.models.login;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.joda.time.Period;

import java.io.Serializable;

public class VPNInfo implements Serializable {

    private final String password;
    private final String name;
    private final int maxTier;
    private final int maxConnect;
    private final String tierName;
    private final int expirationTime;

    public VPNInfo(@JsonProperty(value = "Status", required = true) int status,
                   @JsonProperty(value = "ExpirationTime", required = true) int expirationTime,
                   @JsonProperty(value = "PlanName", required = true) String planName,
                   @JsonProperty(value = "MaxTier", required = true) int maxTier,
                   @JsonProperty(value = "MaxConnect", required = true) int maxConnect,
                   @JsonProperty(value = "Name", required = true) String name,
                   @JsonProperty(value = "GroupID", required = true) String groupId,
                   @JsonProperty(value = "Password", required = true) String password) {
        this.name = name;
        this.password = password;
        this.tierName = planName;
        this.maxConnect = maxConnect;
        this.expirationTime = expirationTime;
        this.maxTier = maxTier;
    }

    boolean isRemainingTimeAccessible() {
        return expirationTime != 0;
    }

    Period getTrialRemainingTime() {
        try {
            Interval interval = new Interval(new DateTime(), new DateTime(expirationTime * 1000L));
            return interval.toPeriod();
        }
        catch (Exception e) {
            return new Period(0, 0, 0, 0);
        }
    }

    public int getMaxTier() {
        return maxTier;
    }

    public String getPassword() {
        return password;
    }

    public String getName() {
        return name;
    }

    public String getTierName() {
        return tierName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof VPNInfo)) {
            return false;
        }

        VPNInfo vpnInfo = (VPNInfo) o;

        return maxTier == vpnInfo.maxTier && (password != null ? password.equals(vpnInfo.password) :
            vpnInfo.password == null) && (name != null ? name.equals(vpnInfo.name) : vpnInfo.name == null)
            && (tierName != null ? tierName.equals(vpnInfo.tierName) : vpnInfo.tierName == null)
            && expirationTime == vpnInfo.expirationTime && maxConnect == vpnInfo.maxConnect;
    }

    @Override
    public int hashCode() {
        int result = password != null ? password.hashCode() : 0;
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + maxTier;
        result = 31 * result + (tierName != null ? tierName.hashCode() : 0);
        result = 31 * result + expirationTime;
        result = 31 * result + maxConnect;
        return result;
    }

    // FIXME API should be sending correct information
    public int getMaxConnect() {
        return tierName.equals("free") ? 2 : maxConnect;
    }
}