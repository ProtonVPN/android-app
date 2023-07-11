/*
 * Copyright (c) 2021. Proton AG
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

package com.protonvpn.android.logging

import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.settings.data.toLogList
import com.protonvpn.android.userstorage.ProfileManager
import com.protonvpn.android.utils.withPrevious
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingChangesLogger @Inject constructor(
    mainScope: CoroutineScope,
    effectiveUserSettings: EffectiveCurrentUserSettings,
    private val profileManager: ProfileManager
) {
    init {
        effectiveUserSettings.effectiveSettings
            .map { it.toLogList(profileManager) }
            .withPrevious()
            .onEach { (old, new) ->
                val changeLogLines = new.filterNot { old.contains(it) }
                if (changeLogLines.isNotEmpty()) {
                    ProtonLogger.log(SettingsChanged, changeLogLines.joinToString("\n"))
                }
            }
            .launchIn(mainScope)
    }

    fun getCurrentSettingsForLog(settings: LocalUserSettings) =
        settings.toLogList(profileManager).joinToString("\n")
}
