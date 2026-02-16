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
package com.protonvpn.android.vpn.protun

import android.content.SharedPreferences
import com.protonvpn.android.utils.SharedPreferencesProvider
import dagger.Reusable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import me.proton.core.util.android.sharedpreferences.PreferencesProvider
import me.proton.core.util.android.sharedpreferences.boolean
import me.proton.core.util.android.sharedpreferences.long
import me.proton.core.util.android.sharedpreferences.observe
import javax.inject.Inject

@Reusable
class PacketCapturePrefs @Inject constructor(
    private val prefsProvider: SharedPreferencesProvider
) : PreferencesProvider {

    override val preferences: SharedPreferences
        get() = prefsProvider.getPrefs(PREFS_NAME)

    var isActive: Boolean by boolean(key = KEY_IS_ACTIVE, default = false)
    val isActiveFlow: Flow<Boolean> = preferences.observe<Boolean>(KEY_IS_ACTIVE).mapNotNull { it ?: isActive }
    var maxBytes: Long by long(key = KEY_MAX_BYTES, default = DEFAULT_MAX_BYTES)
    val maxBytesFlow: Flow<Long> = preferences.observe<Long>(key = KEY_MAX_BYTES).mapNotNull { it ?: maxBytes }

    companion object {
        private const val PREFS_NAME = "PcapPrefs"
        private const val KEY_IS_ACTIVE = "is_active"
        private const val KEY_MAX_BYTES = "max_bytes"

        private const val DEFAULT_MAX_BYTES = 100L * 1024 * 1024 // 100 MB
    }
}
