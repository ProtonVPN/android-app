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

import com.azimolabs.conditionwatcher.ConditionWatcher;
import com.azimolabs.conditionwatcher.Instruction;
import com.protonvpn.android.components.LoaderUI;
import com.protonvpn.android.components.NetworkFrameLayout;
import com.protonvpn.android.models.vpn.VpnCountry;
import com.protonvpn.android.utils.CountryTools;
import com.protonvpn.android.utils.ServerManager;

import java.util.List;

public class NetworkTestHelper extends UIActionsTestHelper {

    static ServerManager serverManager = new ServerManagerHelper().serverManager;

    public static List<VpnCountry> getVpnCountries() {
        return serverManager.getVpnCountries();
    }

    public static VpnCountry getFirstNotAccessibleVpnCountry() {
        return serverManager.getFirstNotAccessibleVpnCountry();
    }

    public static List<VpnCountry> getExitVpnCountries() {
        return serverManager.getSecureCoreExitCountries();
    }

    public static String getEntryVpnCountry(VpnCountry exitCountry) {
        String countryCode = serverManager.getBestScoreServer(exitCountry).getEntryCountry();
        return CountryTools.getFullName(countryCode);
    }

    public static void waitUntilNetworkErrorAppears(LoaderUI loader) {
        Instruction networkErrorInstruction = new Instruction() {
            @Override
            public String getDescription() {
                return "Waiting until network loader returns Error state";
            }

            @Override
            public boolean checkCondition() {
                return loader.getState() == NetworkFrameLayout.State.ERROR;
            }
        };

        checkCondition(networkErrorInstruction);
    }

    private static void checkCondition(Instruction instruction) {
        try {
            ConditionWatcher.waitForCondition(instruction);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
