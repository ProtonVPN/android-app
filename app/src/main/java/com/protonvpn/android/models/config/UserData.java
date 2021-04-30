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

import com.protonvpn.android.ProtonApplication;
import com.protonvpn.android.api.ApiSessionProvider;
import com.protonvpn.android.models.login.LoginResponse;
import com.protonvpn.android.models.login.VpnInfoResponse;
import com.protonvpn.android.models.profiles.Profile;
import com.protonvpn.android.models.vpn.Server;
import com.protonvpn.android.utils.AndroidUtils;
import com.protonvpn.android.utils.LiveEvent;
import com.protonvpn.android.utils.Storage;

import org.jetbrains.annotations.NotNull;
import org.joda.time.DateTime;
import org.joda.time.Days;
import org.joda.time.Minutes;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import me.proton.core.network.domain.session.SessionId;

public final class UserData implements Serializable {

    private String user;
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
    private boolean apiUseDoH;
    private NetShieldProtocol netShieldProtocol;
    private boolean useSmartProtocol;
    private boolean vpnAcceleratorEnabled;
    private boolean showVpnAcceleratorNotifications;

    private transient MutableLiveData<NetShieldProtocol> netShieldProtocolLiveData = new MutableLiveData<>(netShieldProtocol);
    private transient LiveEvent updateEvent = new LiveEvent();
    private transient ApiSessionProvider apiSessionProvider =
        new ApiSessionProvider(ProtonApplication.getAppContext());

    public UserData() {
        user = "";
        mtuSize = 1375;
        showIcon = true;
        splitTunnelApps = new ArrayList<>();
        splitTunnelIpAddresses = new ArrayList<>();
        selectedProtocol = VpnProtocol.IKEv2;
        transmissionProtocol = TransmissionProtocol.TCP;
        useIon = false;
        apiUseDoH = true;
        useSmartProtocol = true;
        vpnAcceleratorEnabled = true;
        showVpnAcceleratorNotifications = true;
    }

    public String getUser() {
        return user;
    }

    public String getVpnUserName() {
        return isLoggedIn ? getVpnInfoResponse().getVpnUserName() : "guest";
    }

    public String getVpnPassword() {
        return isLoggedIn ? getVpnInfoResponse().getPassword() : "guest";
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
        return serverToAccess != null && (getVpnInfoResponse() != null &&
            getVpnInfoResponse().hasAccessToTier(serverToAccess.getTier()));
    }

    public boolean isFreeUser() {
        return getUserTier() == 0;
    }

    public boolean isBasicUser() {
        return getVpnInfoResponse().getUserTier() == 1;
    }

    public boolean isUserPlusOrAbove() {
        return getVpnInfoResponse().getUserTier() > 1;
    }

    public int getUserTier() {
        return getVpnInfoResponse() != null ? getVpnInfoResponse().getUserTier() : 0;
    }

    public boolean isTrialUser() {
        return getVpnInfoResponse().getUserTierName().equals("trial");
    }

    public boolean hasAccessToAnyServer(List<Server> serverList) {
        for (Server server : serverList) {
            if (hasAccessToServer(server)) {
                return true;
            }
        }
        return false;
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

    public void logout() {
        setLoggedIn(false);
        setTrialDialogShownAt(null);
        clearNetworkUserData();
        setDefaultConnection(null);
        setNetShieldProtocol(null);
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

    @Nullable
    public VpnInfoResponse getVpnInfoResponse() {
        return vpnInfoResponse;
    }

    public void setVpnInfoResponse(VpnInfoResponse vpnInfoResponse) {
        this.vpnInfoResponse = vpnInfoResponse;
        if (isFreeUser()) {
            setNetShieldProtocol(NetShieldProtocol.DISABLED);
        }
        if (!isUserPlusOrAbove()) {
            setSecureCoreEnabled(false);
        }
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

    private void setVpnInfoUpdatedAt(DateTime vpnInfoUpdatedAt) {
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

    @NotNull
    public List<String> getSplitTunnelIpAddresses() {
        return splitTunnelIpAddresses;
    }

    @NotNull
    public VpnProtocol getSelectedProtocol() {
        if (useSmartProtocol) {
            return VpnProtocol.Smart;
        }
        return selectedProtocol;
    }

    public boolean getUseSmartProtocol() {
        return useSmartProtocol;
    }

    public void setUseSmartProtocol(boolean value) {
        useSmartProtocol = value;
        saveToStorage();
    }

    public boolean isVpnAcceleratorEnabled() {
        return vpnAcceleratorEnabled;
    }

    public void setVpnAcceleratorEnabled(boolean value) {
        vpnAcceleratorEnabled = value;
        saveToStorage();
    }

    public boolean showVpnAcceleratorNotifications() {
        return showVpnAcceleratorNotifications;
    }

    public void setShowVpnAcceleratorNotifications(boolean value) {
        showVpnAcceleratorNotifications = value;
        saveToStorage();
    }

    @NotNull
    public VpnProtocol getManualProtocol() {
        return selectedProtocol;
    }

    public void setManualProtocol(VpnProtocol value) {
        selectedProtocol = value;
        saveToStorage();
    }

    public TransmissionProtocol getTransmissionProtocol() {
        return transmissionProtocol;
    }

    public void setTransmissionProtocol(TransmissionProtocol transmissionProtocol) {
        this.transmissionProtocol = transmissionProtocol;
        saveToStorage();
    }

    public boolean bypassLocalTraffic() {
        return AndroidUtils.INSTANCE.isTV(ProtonApplication.getAppContext()) || bypassLocalTraffic;
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

    public void setApiUseDoH(boolean value) {
        apiUseDoH = value;
        saveToStorage();
    }

    public boolean getApiUseDoH() {
        return apiUseDoH;
    }

    public void setNetShieldProtocol(NetShieldProtocol value) {
        netShieldProtocol = value;
        netShieldProtocolLiveData.postValue(value);
        saveToStorage();
    }

    public LiveData<NetShieldProtocol> getNetShieldLiveData() {
        return netShieldProtocolLiveData;
    }

    public NetShieldProtocol getNetShieldProtocol() {
        return !isLoggedIn || isFreeUser() ? NetShieldProtocol.DISABLED :
            netShieldProtocol == null ? NetShieldProtocol.ENABLED : netShieldProtocol;
    }

    public ApiSessionProvider getApiSessionProvider() {
        return apiSessionProvider;
    }

    public void clearNetworkUserData() {
        apiSessionProvider.clear();
    }

    public void setLoginResponse(LoginResponse value) {
        apiSessionProvider.setLoginResponse(value);
    }

    @Nullable
    public SessionId getSessionId() {
        return apiSessionProvider.getCurrentSessionId();
    }
}
