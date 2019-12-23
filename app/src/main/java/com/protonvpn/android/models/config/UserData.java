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
package com.protonvpn.android.models.config;

import android.os.Build;

import com.protonvpn.android.models.login.VpnInfoResponse;
import com.protonvpn.android.models.profiles.Profile;
import com.protonvpn.android.models.vpn.Server;
import com.protonvpn.android.utils.LiveEvent;
import com.protonvpn.android.utils.Storage;

import org.joda.time.DateTime;
import org.joda.time.Days;
import org.joda.time.Minutes;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.Nullable;

public final class UserData implements Serializable {

    private String user;
    private boolean rememberMe;
    private boolean connectOnBoot;
    private boolean isLoggedIn;
    private boolean useIon;
    private boolean showIcon;
    private boolean useSplitTunneling;
    private int mtuSize;
    private VpnInfoResponse vpnInfoResponse;
    private List<String> splitTunnelApps;
    private List<String> splitTunnelIpAddresses;
    private Profile defaultConnection;
    private boolean bypassLocalTraffic;
    private int timesAppUsed;
    private DateTime lastTimeAppOpened;
    private DateTime vpnInfoUpdatedAt;
    private DateTime trialDialogShownAt;
    private String selectedCountry;
    private VpnProtocol selectedProtocol;
    private boolean secureCoreEnabled;
    private TransmissionProtocol transmissionProtocol;

    private transient LiveEvent updateEvent = new LiveEvent();

    public UserData() {
        user = "";
        mtuSize = 1375;
        rememberMe = false;
        showIcon = true;
        splitTunnelApps = new ArrayList<>();
        splitTunnelIpAddresses = new ArrayList<>();
        selectedProtocol = VpnProtocol.IKEv2;
        transmissionProtocol = TransmissionProtocol.TCP;
        useIon = false;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
        saveToStorage();
    }

    private void saveToStorage() {
        Storage.save(this);
        updateEvent.emit();
    }

    public void setLoggedIn(VpnInfoResponse response) {
        setVpnInfoResponse(response);
        setLoggedIn(true);
    }

    public boolean hasAccessToServer(@Nullable Server serverToAccess) {
        return serverToAccess != null && getVpnInfoResponse().hasAccessToTier(serverToAccess.getTier())
            && serverToAccess.isOnline();
    }

    public boolean isFreeUser() {
        return getVpnInfoResponse().getUserTier() == 0;
    }

    public boolean isTrialUser() {
        return getVpnInfoResponse().getUserTierName() == "trial";
    }

    public boolean hasAccessToAnyServer(List<Server> serverList) {
        for (Server server : serverList) {
            if (hasAccessToServer(server)) {
                return true;
            }
        }
        return false;
    }

    public boolean isRememberMeEnabled() {
        return rememberMe;
    }

    @Deprecated
    public void trackAppOpening(DateTime currentTime) {
        DateTime lastOpen = lastTimeAppOpened != null ? lastTimeAppOpened : new DateTime();
        int daysBetween = Days.daysBetween(lastOpen, currentTime).getDays();
        if (daysBetween == 1 || timesAppUsed == 0) {
            timesAppUsed++;
            lastTimeAppOpened = currentTime;
        }
        else if (daysBetween > 1) {
            timesAppUsed = 1;
        }
        if (lastTimeAppOpened.isAfter(currentTime)) {
            lastTimeAppOpened = currentTime;
        }
    }

    public int getTimesAppUsed() {
        return timesAppUsed;
    }

    public void setRememberMe(boolean rememberMe) {
        this.rememberMe = rememberMe;
        saveToStorage();
    }

    public void logout() {
        setLoggedIn(false);
        setTrialDialogShownAt(null);
    }

    public boolean isMaxSessionReached(int currentSessionCount) {
        return getVpnInfoResponse().getMaxSessionCount() <= currentSessionCount;
    }

    public Profile getDefaultConnection() {
        return defaultConnection;
    }

    public void setDefaultConnection(Profile profile) {
        defaultConnection = profile;
        saveToStorage();
    }

    public boolean getConnectOnBoot() {
        return Build.VERSION.SDK_INT < 26 && connectOnBoot;
    }

    public void setConnectOnBoot(boolean connectOnBoot) {
        this.connectOnBoot = connectOnBoot;
        saveToStorage();
    }

    public VpnInfoResponse getVpnInfoResponse() {
        return vpnInfoResponse;
    }

    public void setVpnInfoResponse(VpnInfoResponse vpnInfoResponse) {
        this.vpnInfoResponse = vpnInfoResponse;
        this.setVpnInfoUpdatedAt(new DateTime());
        saveToStorage();
    }

    public boolean isLoggedIn() {
        return isLoggedIn;
    }

    public void setLoggedIn(boolean loggedIn) {
        isLoggedIn = loggedIn;
        saveToStorage();
    }

    public boolean shouldShowIcon() {
        return Build.VERSION.SDK_INT >= 26 || showIcon;
    }

    public void setShowIcon(boolean showIcon) {
        this.showIcon = showIcon;
        saveToStorage();
    }

    public LiveEvent getUpdateEvent() {
        return updateEvent;
    }

    public boolean wasVpnInfoRecentlyUpdated(int minutesAgo) {
        return vpnInfoUpdatedAt != null && (
            Minutes.minutesBetween(vpnInfoUpdatedAt, new DateTime()).getMinutes() < minutesAgo);
    }

    public DateTime getVpnInfoUpdatedAt() {
        return vpnInfoUpdatedAt;
    }

    public void setVpnInfoUpdatedAt(DateTime vpnInfoUpdatedAt) {
        this.vpnInfoUpdatedAt = vpnInfoUpdatedAt;
        saveToStorage();
    }

    public boolean wasTrialDialogRecentlyShowed() {
        return trialDialogShownAt != null && (
            Minutes.minutesBetween(trialDialogShownAt, new DateTime()).getMinutes() < 360);
    }

    public void setTrialDialogShownAt(DateTime trialDialogShownAt) {
        this.trialDialogShownAt = trialDialogShownAt;
        saveToStorage();
    }

    public int getMtuSize() {
        return mtuSize;
    }

    public void setMtuSize(int mtuSize) {
        this.mtuSize = mtuSize;
        saveToStorage();
    }

    public boolean getUseSplitTunneling() {
        return useSplitTunneling;
    }

    public void setUseSplitTunneling(boolean useSplitTunneling) {
        this.useSplitTunneling = useSplitTunneling;
        saveToStorage();
    }

    public List<String> getSplitTunnelApps() {
        return splitTunnelApps;
    }

    public void addAppToSplitTunnel(String app) {
        splitTunnelApps.add(app);
        saveToStorage();
    }

    public void addIpToSplitTunnel(String ip) {
        if (!splitTunnelIpAddresses.contains(ip)) {
            this.splitTunnelIpAddresses.add(ip);
            saveToStorage();
        }
    }

    public void removeIpFromSplitTunnel(String ip) {
        splitTunnelIpAddresses.remove(ip);
        saveToStorage();
    }

    public void removeAppFromSplitTunnel(String app) {
        splitTunnelApps.remove(app);
        saveToStorage();
    }

    public List<String> getSplitTunnelIpAddresses() {
        return splitTunnelIpAddresses;
    }

    public boolean isOpenVPNSelected() {
        return !selectedProtocol.equals(VpnProtocol.IKEv2);
    }

    public VpnProtocol getSelectedProtocol() {
        return selectedProtocol;
    }

    public void setSelectedProtocol(VpnProtocol selectedProtocol) {
        this.selectedProtocol = selectedProtocol;
        saveToStorage();
    }

    public String getTransmissionProtocol() {
        return transmissionProtocol.toString();
    }

    public void setTransmissionProtocol(TransmissionProtocol transmissionProtocol) {
        this.transmissionProtocol = transmissionProtocol;
        saveToStorage();
    }

    public boolean bypassLocalTraffic() {
        return bypassLocalTraffic;
    }

    public void setBypassLocalTraffic(boolean bypassLocalTraffic) {
        this.bypassLocalTraffic = bypassLocalTraffic;
        saveToStorage();
    }

    public boolean isSecureCoreEnabled() {
        return secureCoreEnabled;
    }

    public void setSecureCoreEnabled(boolean secureCoreEnabled) {
        if (this.secureCoreEnabled != secureCoreEnabled) {
            this.secureCoreEnabled = secureCoreEnabled;
            saveToStorage();
        }
    }
}