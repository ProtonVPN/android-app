/*
 * Copyright (c) 2021 Proton Technologies AG
 * This file is part of Proton Technologies AG and ProtonCore.
 *
 * ProtonCore is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonCore is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonCore.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.protonvpn.testSuites

import com.protonvpn.tests.account.AccountRobotTests
import com.protonvpn.tests.connection.ConnectionRobotTests
import com.protonvpn.tests.home.HomeRobotTests
import com.protonvpn.tests.login.LoginRobotTests
import com.protonvpn.tests.login.LoginViewModelTest
import com.protonvpn.tests.login.LogoutRobotTests
import com.protonvpn.tests.map.MapRobotTests
import com.protonvpn.tests.onboarding.OnboardingRobotTests
import com.protonvpn.tests.profiles.ProfilesRobotFreeUserTests
import com.protonvpn.tests.profiles.ProfilesRobotTests
import com.protonvpn.tests.secureCore.SecureCoreRobotSecurityTests
import com.protonvpn.tests.secureCore.SecureCoreRobotTests
import com.protonvpn.tests.settings.SettingsRobotTests
import com.protonvpn.tests.upgrade.CheckUpgradeTests
import com.protonvpn.tests.upgrade.UpgradeTests
import com.protonvpn.tests.util.LiveEventTests
import com.protonvpn.tests.util.ProtonLoggerImplTests
import com.protonvpn.tests.vpn.VpnConnectionTests
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
        AccountRobotTests::class,
        ConnectionRobotTests::class,
        HomeRobotTests::class,
        LoginRobotTests::class,
        LoginViewModelTest::class,
        LogoutRobotTests::class,
        MapRobotTests::class,
        OnboardingRobotTests::class,
        ProfilesRobotFreeUserTests::class,
        ProfilesRobotTests::class,
        ProtonLoggerImplTests::class,
        SecureCoreRobotTests::class,
        SecureCoreRobotSecurityTests::class,
        SettingsRobotTests::class,
        CheckUpgradeTests::class,
        UpgradeTests::class,
        VpnConnectionTests::class,
        LiveEventTests::class
)
class MobileSuite
