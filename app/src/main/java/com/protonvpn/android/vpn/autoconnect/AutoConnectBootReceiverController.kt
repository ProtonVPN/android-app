/*
 * Copyright (c) 2025. Proton AG
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

package com.protonvpn.android.vpn.autoconnect

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
import android.content.pm.PackageManager.DONT_KILL_APP
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@Reusable
class AutoConnectBootReceiverController @Inject constructor(
    @ApplicationContext appContext: Context,
    private val mainScope: CoroutineScope,
    effectiveUserSettings: EffectiveCurrentUserSettings
) {

    private val controlFlow = effectiveUserSettings
        .tvAutoConnectOnBoot
        .onEach { autoConnectOnBoot ->
            val receiverName = ComponentName(appContext, AutoConnectBootReceiver::class.java)
            val newState = when {
                autoConnectOnBoot -> COMPONENT_ENABLED_STATE_ENABLED
                else -> COMPONENT_ENABLED_STATE_DISABLED
            }
            appContext.packageManager.setComponentEnabledSetting(receiverName, newState, DONT_KILL_APP)
        }

    fun start() {
        controlFlow.launchIn(mainScope)
    }
}
