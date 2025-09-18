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
import android.net.Uri
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import com.protonvpn.android.R
import com.protonvpn.android.logging.ProfilesAutoOpen
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.profiles.data.ProfileAutoOpen
import com.protonvpn.android.redesign.vpn.AnyConnectIntent
import com.protonvpn.android.ui.ForegroundActivityTracker
import com.protonvpn.android.utils.doesDefaultBrowserSupportEphemeralCustomTabs
import com.protonvpn.android.utils.getEphemeralCustomTabsBrowser
import com.protonvpn.android.utils.openPrivateCustomTab
import com.protonvpn.android.utils.openUrl
import com.protonvpn.android.vpn.ConnectTrigger
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileAutoOpenHandler(
    val mainScope: CoroutineScope,
    val newSessionEvent: SharedFlow<Pair<AnyConnectIntent, ConnectTrigger>>,
    val status: StateFlow<VpnStateMonitor.Status>,
    val getAutoOpenByProfileId: suspend (Long) -> ProfileAutoOpen?,
    val foregroundActivityFlow: StateFlow<Activity?>,
    val getActivityNightMode: (Activity) -> Int = { activity ->
        activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    }
) {
    @Inject constructor(
        mainScope: CoroutineScope,
        vpnStateMonitor: VpnStateMonitor,
        getProfileById: GetProfileById,
        foregroundActivityTracker: ForegroundActivityTracker,
    ) : this(
        mainScope = mainScope,
        newSessionEvent = vpnStateMonitor.newSessionEvent,
        status = vpnStateMonitor.status,
        getAutoOpenByProfileId = { getProfileById(it)?.autoOpen },
        foregroundActivityFlow = foregroundActivityTracker.foregroundActivityFlow,
    )

    fun start() {
        mainScope.launch {
            newSessionEvent.collectLatest { (connectIntent, trigger) ->
                val profileId = connectIntent.profileId
                if (profileId != null && isAutoOpenTrigger(trigger)) {
                    val activity = withTimeoutOrNull(5000) {
                        foregroundActivityFlow.first { it != null }
                    }
                    if (activity != null) {
                        status.first {
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
        getAutoOpenByProfileId(profileId)?.let { autoOpen ->
            when (autoOpen) {
                is ProfileAutoOpen.None -> {}
                is ProfileAutoOpen.App -> {
                    val intent = activity.packageManager.getLaunchIntentForPackage(autoOpen.packageName)
                    if (intent != null) {
                        activity.startActivity(intent)
                    } else {
                        Toast.makeText(
                            activity,
                            R.string.profile_auto_open_app_failed_to_launch,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                is ProfileAutoOpen.Url ->
                    if (autoOpen.openInPrivateMode) {
                        openUrlInPrivateMode(activity, autoOpen.url)
                    } else {
                        activity.openUrl(autoOpen.url)
                    }
            }
        }
    }

    @VisibleForTesting
    fun openUrlInPrivateMode(activity: Activity, url: Uri) {
        val defaultBrowserSupport = activity.doesDefaultBrowserSupportEphemeralCustomTabs()
        val browserPackage =
            if (defaultBrowserSupport) null
            else activity.getEphemeralCustomTabsBrowser(url)
        if (!defaultBrowserSupport && browserPackage == null) {
            ProtonLogger.log(
                ProfilesAutoOpen,
                "No browser found supporting private custom tabs"
            )
        } else {
            val darkTheme = when (getActivityNightMode(activity)) {
                Configuration.UI_MODE_NIGHT_YES -> true
                Configuration.UI_MODE_NIGHT_NO -> false
                else -> null
            }

            if (browserPackage != null) {
                ProtonLogger.log(ProfilesAutoOpen, "Opening private custom tab in $browserPackage")
            } else {
                ProtonLogger.log(ProfilesAutoOpen, "Opening private custom tab in default browser")
            }
            activity.openPrivateCustomTab(url, darkTheme, browserPackage)
        }
    }
}