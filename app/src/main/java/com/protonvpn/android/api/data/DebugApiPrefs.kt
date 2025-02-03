/*
 * Copyright (c) 2023 Proton Technologies AG
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
package com.protonvpn.android.api.data

import android.content.SharedPreferences
import com.protonvpn.android.utils.SharedPreferencesProvider
import kotlinx.coroutines.flow.Flow
import me.proton.core.util.android.sharedpreferences.PreferencesProvider
import me.proton.core.util.android.sharedpreferences.observe
import me.proton.core.util.android.sharedpreferences.string
import javax.inject.Inject

class DebugApiPrefs @Inject constructor(
    private val prefsProvider: SharedPreferencesProvider
) : PreferencesProvider {

    override val preferences: SharedPreferences
        get() = prefsProvider.getPrefs(PREFS_NAME)

    var netzone: String? by string(key = KEY_NETZONE)
    var country: String? by string(key = KEY_COUNTRY)

    val netzoneFlow: Flow<String?> = preferences.observe<String?>(KEY_NETZONE)
    val countryFlow: Flow<String?> = preferences.observe<String?>(KEY_COUNTRY)

    companion object {
        private const val PREFS_NAME = "DebugApiPrefs"
        private const val KEY_NETZONE = "netzone"
        private const val KEY_COUNTRY = "country"
    }
}
