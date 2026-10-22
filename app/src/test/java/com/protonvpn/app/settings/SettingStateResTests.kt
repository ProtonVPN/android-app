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

package com.protonvpn.app.settings

import com.protonvpn.android.R
import com.protonvpn.android.redesign.settings.ui.customDnsStateRes
import com.protonvpn.android.redesign.settings.ui.netShieldStateRes
import com.protonvpn.android.vpn.DnsOverride
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingStateResTests {

    @Test
    fun `NetShield state reflects the setting when there is no DNS override`() {
        assertEquals(R.string.netshield_state_on, netShieldStateRes(true, DnsOverride.None))
        assertEquals(R.string.netshield_state_off, netShieldStateRes(false, DnsOverride.None))
    }

    @Test
    fun `NetShield is unavailable when DNS is overridden`() {
        listOf(DnsOverride.CustomDns, DnsOverride.SystemPrivateDns).forEach { dnsOverride ->
            assertEquals(R.string.netshield_state_unavailable, netShieldStateRes(true, dnsOverride))
            assertEquals(R.string.netshield_state_unavailable, netShieldStateRes(false, dnsOverride))
        }
    }

    @Test
    fun `custom DNS state reflects the setting when private DNS is not active`() {
        assertEquals(R.string.custom_dns_state_on, customDnsStateRes(isEnabled = true, isPrivateDnsActive = false))
        assertEquals(R.string.custom_dns_state_off, customDnsStateRes(isEnabled = false, isPrivateDnsActive = false))
    }

    @Test
    fun `custom DNS is unavailable when private DNS is active`() {
        assertEquals(
            R.string.custom_dns_state_unavailable,
            customDnsStateRes(isEnabled = true, isPrivateDnsActive = true)
        )
        assertEquals(
            R.string.custom_dns_state_unavailable,
            customDnsStateRes(isEnabled = false, isPrivateDnsActive = true)
        )
    }
}
