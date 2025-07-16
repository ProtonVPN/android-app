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

import com.protonvpn.tests.api.FeatureFlagUserCountryTestsIntegration
import com.protonvpn.tests.login.mobile.RefreshTokenTests
import com.protonvpn.tests.bugReport.BugReportMockedTests
import com.protonvpn.tests.logging.ProtonLoggerImplTestsIntegration
import com.protonvpn.tests.login.mobile.LogoutTestsMocked
import com.protonvpn.tests.servers.ServerListUpdaterTests
import com.protonvpn.tests.telemetry.TelemetryCacheTestsIntegration
import com.protonvpn.tests.vpn.VpnConnectionTestsIntegration
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
// Tests to update and reenable later:
//    HomeActivityPromoOfferTests::class,
//    PartnershipTests::class,
//    PromoOfferActivityTests::class,
    FeatureFlagUserCountryTestsIntegration::class,
    // Note: when we have a lot of isolated tests they can be run in a separate CI job without test orchestrator.
    IsolatedTestsSuite::class,
    LogoutTestsMocked::class,
    BugReportMockedTests::class,
    ProtonLoggerImplTestsIntegration::class,
    RefreshTokenTests::class,
    ServerListUpdaterTests::class,
    TelemetryCacheTestsIntegration::class,
    VpnConnectionTestsIntegration::class,
)
class MobileMockApiSuite
