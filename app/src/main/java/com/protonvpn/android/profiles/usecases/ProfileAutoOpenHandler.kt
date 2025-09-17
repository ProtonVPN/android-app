/*
 * Copyright (c) 2024 Proton AG
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

package com.protonvpn.android.profiles.usecases

import android.app.Activity
import android.content.res.Configuration
import com.protonvpn.android.profiles.data.ProfileAutoOpen
import com.protonvpn.android.profiles.data.ProfilesDao
import com.protonvpn.android.ui.ForegroundActivityTracker
import com.protonvpn.android.utils.openPrivateCustomTab
import com.protonvpn.android.utils.openUrl
import com.protonvpn.android.vpn.ConnectTrigger
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileAutoOpenHandler @Inject constructor(
    val mainScope: CoroutineScope,
    val vpnStateMonitor: VpnStateMonitor,
    val profilesDao: ProfilesDao,
    val foregroundActivityTracker: ForegroundActivityTracker,
) {

    fun start() {
        mainScope.launch {
            vpnStateMonitor.newSessionEvent.collectLatest { (connectIntent, trigger) ->
                val profileId = connectIntent.profileId
                if (profileId != null && isAutoOpenTrigger(trigger)) {
                    val activity = withTimeoutOrNull(5000) {
                        foregroundActivityTracker.foregroundActivityFlow.first { it != null }
                    }
                    if (activity != null) {
                        vpnStateMonitor.status.first {
                            it.state == VpnState.Connected && profileId == it.connectIntent?.profileId
                        }.run {
                            handleAutoOpenForProfile(activity, profileId)
                        }
                    }
                }
            }
        }
    }

    private fun isAutoOpenTrigger(trigger: ConnectTrigger) = when(trigger) {
        is ConnectTrigger.Auto,
        is ConnectTrigger.Fallback,
        ConnectTrigger.Reconnect,
        ConnectTrigger.GuestHole ->
            false
        else ->
            true
    }

    private suspend fun handleAutoOpenForProfile(activity: Activity, profileId: Long) {
        profilesDao.getProfileById(profileId)?.let { profile ->
            val autoOpen = profile.autoOpen
            when (autoOpen) {
                is ProfileAutoOpen.None -> {}
                is ProfileAutoOpen.App ->
                    activity.packageManager.getLaunchIntentForPackage(autoOpen.packageName)?.let { intent ->
                        activity.startActivity(intent)
                    }
                is ProfileAutoOpen.Url ->
                    if (autoOpen.openInPrivateMode) {
                        val currentNightMode = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                        val darkTheme = when (currentNightMode) {
                            Configuration.UI_MODE_NIGHT_YES -> true
                            Configuration.UI_MODE_NIGHT_NO -> false
                            else -> null
                        }
                        activity.openPrivateCustomTab(autoOpen.url, darkTheme)
                    } else {
                        activity.openUrl(profile.autoOpen.url)
                    }
            }
        }
    }
}