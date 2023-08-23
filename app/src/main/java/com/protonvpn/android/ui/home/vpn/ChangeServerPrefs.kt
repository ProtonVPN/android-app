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
package com.protonvpn.android.ui.home.vpn

import android.content.SharedPreferences
import com.protonvpn.android.utils.SharedPreferencesProvider
import dagger.Reusable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import me.proton.core.util.android.sharedpreferences.PreferencesProvider
import me.proton.core.util.android.sharedpreferences.int
import me.proton.core.util.android.sharedpreferences.long
import me.proton.core.util.android.sharedpreferences.observe
import javax.inject.Inject

@Reusable
class ChangeServerPrefs @Inject constructor(
    private val prefsProvider: SharedPreferencesProvider
) : PreferencesProvider {

    override val preferences: SharedPreferences
        get() = prefsProvider.getPrefs(PREFS_NAME)

    val lastChangeTimestampFlow: Flow<Long> = preferences.observe<Long>(KEY_LAST_CHANGE_TIMESTAMP).filterNotNull()
    var lastChangeTimestamp: Long by long(0, key = KEY_LAST_CHANGE_TIMESTAMP)
    var changeCounter: Int by int(0, key = KEY_CHANGE_COUNTER)
    var changeCounterFlow: Flow<Int> = preferences.observe<Int>(KEY_CHANGE_COUNTER).filterNotNull()
    companion object {
        private const val PREFS_NAME = "ChangeServerPrefs"
        private const val KEY_CHANGE_COUNTER = "changeCounter"
        private const val KEY_LAST_CHANGE_TIMESTAMP = "changeTimestamp"
    }
}
