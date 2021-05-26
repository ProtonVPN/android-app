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
