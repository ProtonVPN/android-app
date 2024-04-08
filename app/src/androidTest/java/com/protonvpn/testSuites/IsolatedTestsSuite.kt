/*
 * Copyright (c) 2023. Proton AG
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

import com.protonvpn.tests.netshield.NetShieldComponentTests
import com.protonvpn.tests.promooffer.PromoOfferCountDownTests
import com.protonvpn.tests.redesign.base.ui.ProtonTextFieldTests
import com.protonvpn.tests.redesign.base.ui.nav.NavigationTests
import com.protonvpn.tests.redesign.recents.RecentsListUiTests
import com.protonvpn.tests.redesign.recents.RecentsListValidatorTests
import com.protonvpn.tests.redesign.vpn.ui.ConnectionDetailsTests
import com.protonvpn.tests.redesign.vpn.ui.GetConnectIntentViewStateTests
import com.protonvpn.tests.redesign.vpn.ui.VpnStatusViewTests
import org.junit.runner.RunWith
import org.junit.runners.Suite

// These tests don't run the whole application and are fully isolated, i.e. leave no storage, register WorkManager jobs,
// global listeners (e.g. application lifecycle) etc.
// Therefore they can be run without test orchestrator when their amount is large enough to justify a separate CI job.
@RunWith(Suite::class)
@Suite.SuiteClasses(
    ConnectionDetailsTests::class,
    GetConnectIntentViewStateTests::class,
    NavigationTests::class,
    NetShieldComponentTests::class,
    PromoOfferCountDownTests::class,
    ProtonTextFieldTests::class,
    RecentsListUiTests::class,
    RecentsListValidatorTests::class,
    VpnStatusViewTests::class,
)
class IsolatedTestsSuite
