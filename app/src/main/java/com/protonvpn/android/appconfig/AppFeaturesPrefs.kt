/*
 * Copyright (c) 2022 Proton AG
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

package com.protonvpn.android.appconfig

import android.content.SharedPreferences
import com.protonvpn.android.utils.SharedPreferencesProvider
import dagger.Reusable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.proton.core.util.android.sharedpreferences.PreferencesProvider
import me.proton.core.util.android.sharedpreferences.boolean
import me.proton.core.util.android.sharedpreferences.long
import me.proton.core.util.android.sharedpreferences.observe
import me.proton.core.util.android.sharedpreferences.string
import javax.inject.Inject

@Reusable
class AppFeaturesPrefs @Inject constructor(
    private val prefsProvider: SharedPreferencesProvider,
) : PreferencesProvider {
    override val preferences: SharedPreferences get() = prefsProvider.getPrefs(PREFS_NAME)

    var purchaseEnabled: Boolean by boolean(false)
    var lastAppStartTimestamp: Long by long(0)

    var showIKEv2Migration: Boolean by boolean(false)

    var lastSuccessfulGuestHoleServerId: String? by string(null)

    var lastAppInUseTimestamp: Long by long(Long.MAX_VALUE)

    var showWhatsNew: Boolean by boolean(default = true, key = KEY_SHOW_WHATS_NEW)
    val showWhatsNewFlow: Flow<Boolean> = preferences.observe<Boolean>(KEY_SHOW_WHATS_NEW).map { it ?: true }

    var wasLaunchedForTv by boolean(default = false)

    companion object {
        private const val PREFS_NAME = "AppFeaturePrefs"
        private const val KEY_SHOW_WHATS_NEW = "showWhatsNew"
    }
}
