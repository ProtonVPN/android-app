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

package com.protonvpn.android.search

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.utils.AndroidUtils.registerBroadcastReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateServersOnStartAndLocaleChange @Inject constructor(
    scope: CoroutineScope,
    @ApplicationContext appContext: Context,
    serverListUpdater: ServerListUpdater,
    currentUser: CurrentUser
) {

    init {
        serverListUpdater.onAppStart()
        appContext.registerBroadcastReceiver(IntentFilter(Intent.ACTION_LOCALE_CHANGED)) {
            scope.launch {
                if (currentUser.isLoggedIn())
                    serverListUpdater.updateServerList()
            }
        }
    }
}
