/*
 * Copyright (c) 2022. Proton AG
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

package com.protonvpn.android.vpn

import android.content.Context
import com.protonvpn.android.auth.data.hasAccessToServer
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.servers.ServerManager2
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.userstorage.ProfileManager
import com.protonvpn.android.utils.AndroidUtils.isTV
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.UserPlanManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("UseDataClass")
@Singleton
class UpdateSettingsOnVpnUserChange @Inject constructor(
    @ApplicationContext private val context: Context,
    mainScope: CoroutineScope,
    private val currentUser: CurrentUser,
    private val serverManager: ServerManager2,
    private val profileManager: ProfileManager,
    private val userSettingsManager: CurrentUserLocalSettingsManager,
    private val userPlanManager: UserPlanManager
) {
    init {
        mainScope.launch {
            currentUser.vpnUserFlow.collect { vpnUser ->
                if (vpnUser != null) {
                    val defaultProfileServer =
                        serverManager.getServerForProfile(profileManager.getDefaultOrFastest(), vpnUser)
                    userSettingsManager.update { current ->
                        // Note: when a different user logs in they will initially have the other user's server list
                        // so it's likely the defaultProfileServer isn't found and the default profile gets reset.
                        val resetDefaultProfile =
                            current.defaultProfileId != null &&
                                (defaultProfileServer == null || !vpnUser.hasAccessToServer(defaultProfileServer))
                        if (resetDefaultProfile) {
                            val reason = when {
                                defaultProfileServer == null -> "the server no longer exists"
                                else -> "the user no longer has access to the profile's server"
                            }
                            ProtonLogger.logCustom(LogCategory.SETTINGS, "reset default profile: $reason")
                        }

                        current.copy(
                            defaultProfileId = current.orDefaultIf(resetDefaultProfile) { it.defaultProfileId },
                            netShield = current.orDefaultIf(vpnUser.isFreeUser) { it.netShield },
                            randomizedNat = current.orDefaultIf(vpnUser.isFreeUser) { it.randomizedNat },
                            safeMode = current.orDefaultIf(vpnUser.isFreeUser) { it.safeMode },
                            secureCore = current.orDefaultIf(!vpnUser.isUserPlusOrAbove) { it.secureCore },
                        )
                    }
                }
            }
        }
        mainScope.launch {
            userPlanManager.planChangeFlow.collect { planChange ->
                if (planChange.oldUser.isFreeUser && !planChange.newUser.isFreeUser && !context.isTV()) {
                    userSettingsManager.updateNetShield(Constants.DEFAULT_NETSHIELD_AFTER_UPGRADE)
                }
            }
        }
    }

    private fun <T> LocalUserSettings.orDefaultIf(takeDefault: Boolean, getter: (LocalUserSettings) -> T ): T =
        if (takeDefault) getter(LocalUserSettings.Default)
        else getter(this)
}
