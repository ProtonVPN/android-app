/*
 * Copyright (c) 2023. Proton AG
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

package com.protonvpn.android.redesign.recents

import com.protonvpn.android.redesign.stubs.toConnectIntent
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import com.protonvpn.android.utils.ServerManager
import dagger.Reusable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@Suppress("UseDataClass")
@Reusable
class GetMostRecentConnectIntent @Inject constructor(
    serverManager: ServerManager,
    effectiveUserSettings: EffectiveCurrentUserSettings
) {
    val mostRecent: Flow<ConnectIntent?> = effectiveUserSettings.effectiveSettings
        .map { settings -> serverManager.defaultConnection.toConnectIntent(serverManager, settings) }
}
