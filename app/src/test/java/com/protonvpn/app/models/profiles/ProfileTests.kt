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

package com.protonvpn.app.models.profiles

import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.profiles.ProfileColor
import com.protonvpn.android.models.profiles.ServerWrapper
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProfileTests {

    @Test
    fun `when migrating old profiles isSecureCore is set on user profiles`() {
        val wrapper = ServerWrapper.makeFastestForCountry("sw")
        val profile = Profile("test", null, wrapper, ProfileColor.FERN.id, null)

        val migratedProfile = profile.migrateFromOlderVersion()
        assertEquals(false, migratedProfile.isSecureCore)
    }

    @Test
    fun `when migrating old profiles isSecureCore is not set on prebaked profiles`() {
        val wrapper = ServerWrapper.makePreBakedFastest()
        val profile = Profile("test", null, wrapper, ProfileColor.FERN.id, null)

        val migratedProfile = profile.migrateFromOlderVersion()
        assertNull(migratedProfile.isSecureCore)
    }
}
