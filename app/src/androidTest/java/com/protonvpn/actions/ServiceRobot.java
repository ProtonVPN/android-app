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
package com.protonvpn.actions;

import com.protonvpn.android.models.vpn.VpnCountry;
import com.protonvpn.results.ConnectionResult;
import com.protonvpn.testsHelper.NetworkTestHelper;
import com.protonvpn.testsHelper.ServiceTestHelper;

import androidx.test.rule.ServiceTestRule;

public class ServiceRobot {

    public ConnectionResult disconnectFromVpnThroughBackend() {
        ServiceTestHelper serviceTestHelper = new ServiceTestHelper(new ServiceTestRule());
        serviceTestHelper.disconnectFromServer();
        return new ConnectionResult();
    }

    public void setVpnServiceAsUnreachable() {
        new ServiceTestHelper(new ServiceTestRule()).setVpnServiceAsUnreachable();
    }

    public String getFirstCountryFromBackend() {
        return NetworkTestHelper.getVpnCountries().get(0).getCountryName();
    }

    public String getSecureCoreEntryCountryFromBackend(VpnCountry country) {
        return NetworkTestHelper.getEntryVpnCountry(country);
    }

    public VpnCountry getFirstSecureCoreExitCountryFromBackend() {
        return NetworkTestHelper.getExitVpnCountries().get(0);
    }

    public String getFirstNotAccessibleVpnCountryFromBackend() {
        return NetworkTestHelper.getFirstNotAccessibleVpnCountry().getCountryName();
    }

    public ConnectionRobot waitForServiceToTryToConnect() {
        new ServiceTestHelper(new ServiceTestRule()).waitUntilServiceIsTryingToConnect();
        return new ConnectionRobot();
    }

    public ConnectionRobot waitUntilServiceIsUnreachable() {
        new ServiceTestHelper(new ServiceTestRule()).waitUntilServiceIsUnreachable();
        return new ConnectionRobot();
    }

    public void deleteCreatedProfiles() {
        ServiceTestHelper.deleteCreatedProfiles();
    }
}
