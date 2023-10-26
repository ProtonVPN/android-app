/*
 * Copyright (c) 2022 Proton Technologies AG
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
package com.protonvpn.android.ui.onboarding

import android.content.SharedPreferences
import com.protonvpn.android.utils.SharedPreferencesProvider
import me.proton.core.util.android.sharedpreferences.PreferencesProvider
import me.proton.core.util.android.sharedpreferences.boolean
import me.proton.core.util.android.sharedpreferences.int
import me.proton.core.util.android.sharedpreferences.long
import javax.inject.Inject

class ReviewTrackerPrefs @Inject constructor(
    private val prefsProvider: SharedPreferencesProvider
) : PreferencesProvider {

    override val preferences: SharedPreferences
        get() = prefsProvider.getPrefs(PREFS_NAME)

    // At the moment install time is unusable, but let's add it as this comes up multiple times in discussions
    // So it may be worth having this in future
    var installTimestamp: Long by long(System.currentTimeMillis())

    var firstConnectionTimestamp: Long by long(0)
    var lastReviewTimestamp: Long by long(0)
    var longSessionReached: Boolean by boolean(false)
    var successConnectionsInRow: Int by int(0)


    companion object {
        private const val PREFS_NAME = "ReviewTrackerPrefs"
    }
}