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

package com.protonvpn.android.ui.home

import android.content.SharedPreferences
import com.protonvpn.android.utils.SharedPreferencesProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.proton.core.util.android.sharedpreferences.PreferencesProvider
import me.proton.core.util.android.sharedpreferences.long
import me.proton.core.util.android.sharedpreferences.observe
import me.proton.core.util.android.sharedpreferences.string
import me.proton.core.util.kotlin.EMPTY_STRING
import javax.inject.Inject

class ServerListUpdaterPrefs @Inject constructor(
    private val prefsProvider: SharedPreferencesProvider
) : PreferencesProvider {

    override val preferences: SharedPreferences
        get() = prefsProvider.getPrefs(PREFS_NAME)

    val ipAddressFlow: Flow<String> = preferences.observe<String>(KEY_IP_ADDRESS).map { it ?: "" }
    var ipAddress: String by string(EMPTY_STRING, key = KEY_IP_ADDRESS)
    var lastKnownCountry: String? by string()
    var lastKnownIsp: String? by string()
    var loadsUpdateTimestamp: Long by long(0)
    var lastNetzoneForLogicals: String? by string()

    companion object {
        private const val PREFS_NAME = "ServerListUpdater"
        private const val KEY_IP_ADDRESS = "ipAddress"
    }
}
