/*
 * Copyright (c) 2019 Proton Technologies AG
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
package com.protonvpn.tests.testRules;

import com.protonvpn.android.ui.home.HomeActivity;
import com.protonvpn.android.vpn.VpnStateMonitor;
import com.protonvpn.testsHelper.ServiceTestHelper;
import com.protonvpn.testsHelper.UserDataHelper;

import org.junit.runner.Description;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.test.rule.ActivityTestRule;
import androidx.test.rule.ServiceTestRule;

public class ProtonHomeActivityTestRule extends InstantTaskExecutorRule {

    private ServiceTestHelper service;
    private UserDataHelper userDataHelper = new UserDataHelper();

    public ActivityTestRule<HomeActivity> activityTestRule =
        new ActivityTestRule<>(HomeActivity.class, false, false);

    @Override
    protected void starting(Description description) {
        super.starting(description);
        if (service == null) {
            service = new ServiceTestHelper(new ServiceTestRule());
        }
        TestIntent testIntent = new TestIntent();
        activityTestRule.launchActivity(testIntent.getIntent());
    }

    @Override
    protected void finished(Description description) {
        super.finished(description);
        service.enableSecureCore(false);
        ServiceTestHelper.stateMonitor.disconnect();
        service.disconnectFromServer();
        userDataHelper.userData.logout();
        ServiceTestHelper.deleteCreatedProfiles();
        activityTestRule.finishActivity();
    }

    public void mockStatusOnConnect(VpnStateMonitor.State state) {
        service.mockVpnBackend.setStateOnConnect(state);
    }

    public void mockErrorOnConnect(VpnStateMonitor.ErrorState errorState) {
        service.mockVpnBackend.setErrorOnConnect(errorState);
    }

    public HomeActivity getActivity() {
        return activityTestRule.getActivity();
    }
}
