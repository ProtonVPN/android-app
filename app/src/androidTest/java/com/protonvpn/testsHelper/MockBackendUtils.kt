/*
 * Copyright (c) 2026. Proton AG
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

package com.protonvpn.testsHelper

import com.protonvpn.mocks.MockRuleBuilder
import com.protonvpn.test.shared.TestUser
import com.protonvpn.testRules.ProtonHiltAndroidRule
import me.proton.core.featureflag.data.remote.resource.UnleashToggleResource
import me.proton.core.featureflag.data.remote.resource.UnleashVariantResource
import me.proton.core.featureflag.data.remote.response.GetUnleashTogglesResponse
import me.proton.core.network.domain.ResponseCodes

fun MockRuleBuilder.featureFlagsResponseRule(
    vararg flags: Pair<String, Boolean>
) {
    val toggles = flags.map {
        UnleashToggleResource(
            name = it.first,
            variant = UnleashVariantResource(name = it.first, enabled = it.second)
        )
    }
    rule(get, path eq "/feature/v2/frontend") {
        respond(GetUnleashTogglesResponse(ResponseCodes.OK, toggles))
    }
}

fun ProtonHiltAndroidRule.setUser(user: TestUser) {
    UserDataHelper().setUserData(user)
    mockDispatcher.prependRules {
        rule(get, path eq "/vpn/v2") { respond(user.vpnInfoResponse) }
    }
}