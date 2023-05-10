/*
 * Copyright (c) 2023 Proton AG
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

package com.protonvpn.android.redesign.settings.data

import android.content.SharedPreferences
import com.protonvpn.android.utils.SharedPreferencesProvider
import dagger.Reusable
import me.proton.core.util.android.sharedpreferences.PreferencesProvider
import me.proton.core.util.android.sharedpreferences.boolean
import javax.inject.Inject

@Reusable
class SharedSettingsPrefs @Inject constructor(
    private val prefsProvider: SharedPreferencesProvider
) : PreferencesProvider {

    override val preferences: SharedPreferences
        get() = prefsProvider.getPrefs(PREFS_NAME)

    var dialogSignOutNotAskAgain: Boolean by boolean(false)

    companion object {
        private const val PREFS_NAME = "SharedSettingsPrefs"
    }
}
