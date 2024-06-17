/*
 * Copyright (c) 2024. Proton AG
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

package com.protonvpn.app.settings.data

import com.protonvpn.android.settings.data.SplitTunnelingMode
import com.protonvpn.android.settings.data.SplitTunnelingSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitTunnelingSettingsTests {

    private val ips1 = listOf("1.2.3.4", "100.0.0.1")
    private val ips2 = listOf("1.1.1.1")
    private val apps1 = listOf("app1", "app2")
    private val apps2 = listOf("app3")

    @Test
    fun `effectively same - both disabled are always equal`() {
        val a = SplitTunnelingSettings(includedIps = ips1, includedApps = apps1)
        val b = SplitTunnelingSettings(includedApps = apps2, excludedIps = ips2)
        assertSameBothWays(a, b)
    }

    @Test
    fun `effectively same - identical enabled are always equal`() {
        val a = SplitTunnelingSettings(
            isEnabled = true,
            includedIps = ips1,
            includedApps = apps1,
            excludedIps = ips2,
            excludedApps = apps2
        )
        val b = a.copy()
        assertSameBothWays(a, b)
    }

    @Test
    fun `effectively same - difference in enabled status with exclude-only is unequal`() {
        val a = SplitTunnelingSettings(
            isEnabled = true,
            mode = SplitTunnelingMode.EXCLUDE_ONLY,
            excludedIps = ips1,
            excludedApps = apps1
        )
        val b = a.copy(isEnabled = false)
        assertDifferBothWays(a, b)
    }

    @Test
    fun `effectively same - difference in enabled status with include-only is unequal`() {
        val a = SplitTunnelingSettings(
            isEnabled = true,
            mode = SplitTunnelingMode.INCLUDE_ONLY,
            includedIps = ips1,
            includedApps = apps1,
        )
        val b = a.copy(isEnabled = false)
        assertDifferBothWays(a, b)
    }

    @Test
    fun `effectively same - difference in mode only is unequal`() {
        val a = SplitTunnelingSettings(
            isEnabled = true,
            mode = SplitTunnelingMode.EXCLUDE_ONLY,
            includedIps = ips1,
            includedApps = apps1,
            excludedIps = ips1,
            excludedApps = apps1
        )
        val b = a.copy(mode = SplitTunnelingMode.INCLUDE_ONLY)
        assertDifferBothWays(a, b)
    }

    @Test
    fun `effectively same - difference in excluded for exclude-only is unequal`() {
        val a = SplitTunnelingSettings(
            isEnabled = true,
            mode = SplitTunnelingMode.EXCLUDE_ONLY,
            excludedIps = ips1,
            excludedApps = apps1
        )
        val b = a.copy(excludedApps = apps2)
        assertDifferBothWays(a, b)
    }

    @Test
    fun `effectively same - difference in included for include-only is unequal`() {
        val a = SplitTunnelingSettings(
            isEnabled = true,
            mode = SplitTunnelingMode.INCLUDE_ONLY,
            includedIps = ips1,
            includedApps = apps1
        )
        val b = a.copy(includedApps = apps2)
        assertDifferBothWays(a, b)
    }

    @Test
    fun `effectively same - difference in excluded for include-only is ignored`() {
        val a = SplitTunnelingSettings(
            isEnabled = true,
            mode = SplitTunnelingMode.INCLUDE_ONLY,
            includedIps = ips1,
            includedApps = apps1
        )
        val b = a.copy(excludedApps = apps2)
        assertSameBothWays(a, b)
    }

    @Test
    fun `effectively same - difference in included for exclude-only is ignored`() {
        val a = SplitTunnelingSettings(
            isEnabled = true,
            mode = SplitTunnelingMode.EXCLUDE_ONLY,
            excludedIps = ips1,
            excludedApps = apps1
        )
        val b = a.copy(includedApps = apps2)
        assertSameBothWays(a, b)
    }

    @Test
    fun `effectively same - difference in enabled for exclude-only with empty excludes is ignored`() {
        val a = SplitTunnelingSettings(
            isEnabled = true,
            mode = SplitTunnelingMode.EXCLUDE_ONLY,
        )
        val b = a.copy(isEnabled = false)
        assertSameBothWays(a, b)
    }

    @Test
    fun `effectively same - difference in enabled for include-only with empty includes is ignored`() {
        val a = SplitTunnelingSettings(
            isEnabled = true,
            mode = SplitTunnelingMode.INCLUDE_ONLY,
        )
        val b = a.copy(isEnabled = false)
        assertSameBothWays(a, b)
    }

    @Test
    fun `effectively same - empty includes on one side only are unequal`() {
        val a = SplitTunnelingSettings(
            isEnabled = true,
            mode = SplitTunnelingMode.INCLUDE_ONLY,
        )
        val b = a.copy(includedApps = listOf("apps"))
        assertDifferBothWays(a, b)
    }

    private fun assertSameBothWays(a: SplitTunnelingSettings, b: SplitTunnelingSettings) {
        assertTrue(a.isEffectivelySameAs(b))
        assertTrue(b.isEffectivelySameAs(a))
    }

    private fun assertDifferBothWays(a: SplitTunnelingSettings, b: SplitTunnelingSettings) {
        assertFalse(a.isEffectivelySameAs(b))
        assertFalse(b.isEffectivelySameAs(a))
    }
}
