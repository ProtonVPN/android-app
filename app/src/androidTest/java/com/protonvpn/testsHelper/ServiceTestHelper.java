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
package com.protonvpn.testsHelper;

import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import com.azimolabs.conditionwatcher.ConditionWatcher;
import com.protonvpn.android.models.config.UserData;
import com.protonvpn.android.ui.home.HomeActivity;
import com.protonvpn.android.utils.ServerManager;
import com.protonvpn.android.vpn.VpnStateMonitor;
import com.protonvpn.conditions.NetworkInstruction;
import com.protonvpn.mocks.MockVpnBackend;

import org.strongswan.android.logic.VpnStateService;

import androidx.test.InstrumentationRegistry;
import androidx.test.rule.ServiceTestRule;

public class ServiceTestHelper {

    private VpnStateService service;
    static ServerManagerHelper helper = new ServerManagerHelper();
    public static VpnStateMonitor stateMonitor = helper.vpnStateMonitor;
    static ServerManager serverManager = helper.serverManager;
    static UserData userData = helper.userData;
    public MockVpnBackend mockVpnBackend = helper.mockVpnBackendProvider.getBackend();

    public ServiceTestHelper(ServiceTestRule serviceRule) {
        service = initService(serviceRule);
    }

    private VpnStateService initService(ServiceTestRule serviceRule) {
        IBinder binder;
        Intent serviceIntent = new Intent(InstrumentationRegistry.getTargetContext(), VpnStateService.class);
        serviceIntent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);

        try {
            binder = serviceRule.bindService(serviceIntent);
        }
        catch (Exception e) {
            binder = null;
        }

        return ((VpnStateService.LocalBinder) binder).getService();
    }

    public void setVpnServiceAsUnreachable() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            service.disconnect();
            service.setState(VpnStateService.State.DISABLED);
            service.setError(VpnStateService.ErrorState.UNREACHABLE);
        }, 100);
    }

    public void checkIfConnectedToVPN() {
        try {
            ConditionWatcher.waitForCondition(new NetworkInstruction() {
                @Override
                public boolean checkCondition() {
                    return stateMonitor.isConnected();
                }
            });
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void enableSecureCore(boolean state) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            userData.setSecureCoreEnabled(state);
        }, 100);
    }

    public void checkIfDisconnectedFromVPN() {
        try {
            ConditionWatcher.waitForCondition(new NetworkInstruction() {
                @Override
                public boolean checkCondition() {
                    return !stateMonitor.isConnected();
                }
            });
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void waitUntilServiceIsTryingToConnect() {
        try {
            ConditionWatcher.waitForCondition(new NetworkInstruction() {
                @Override
                public boolean checkCondition() {
                    return service.getState() == VpnStateService.State.CONNECTING;
                }
            });
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void waitUntilServiceIsUnreachable() {
        try {
            ConditionWatcher.waitForCondition(new NetworkInstruction() {
                @Override
                public boolean checkCondition() {
                    return service.getErrorState() == VpnStateService.ErrorState.UNREACHABLE;
                }
            });
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    // TODO: move to the Unit tests part
    public void disconnectFromServer() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> service.disconnect(), 100);
    }

    public static void deleteCreatedProfiles() {
        serverManager.deleteSavedProfiles();
    }

    public static boolean isSecureCoreEnabled() {
        return userData.isSecureCoreEnabled();
    }

    public static void getExpiredTrialUserNotification(HomeActivity activity) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            activity.shouldExpiredDialog(TestUser.setTrialUserAsExpired().getVpnInfoResponse());
        }, 0);
    }
}
