/*
 * Copyright (c) 2022. Proton AG
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

package com.protonvpn.testSuites

import com.protonvpn.tests.account.AccountTests
import com.protonvpn.tests.connection.ConnectionTests
import com.protonvpn.tests.logging.ProtonLoggerImplTests
import com.protonvpn.tests.login.LogoutTests
import com.protonvpn.tests.map.MapTests
import com.protonvpn.tests.profiles.ProfileTests
import com.protonvpn.tests.promooffer.HomeActivityPromoOfferTests
import com.protonvpn.tests.promooffer.PromoOfferActivityTests
import com.protonvpn.tests.secureCore.SecureCoreSecurityTests
import com.protonvpn.tests.secureCore.SecureCoreTests
import com.protonvpn.tests.settings.SettingsTests
import com.protonvpn.tests.util.LiveEventTests
import com.protonvpn.tests.vpn.VpnConnectionTests
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    AccountTests::class,
    ConnectionTests::class,
    HomeActivityPromoOfferTests::class,
    LiveEventTests::class,
    LogoutTests::class,
    MapTests::class,
    ProfileTests::class,
    PromoOfferActivityTests::class,
    ProtonLoggerImplTests::class,
    SecureCoreTests::class,
    SecureCoreSecurityTests::class,
    SettingsTests::class,
    VpnConnectionTests::class,
)
class MobileMockApiSuite
