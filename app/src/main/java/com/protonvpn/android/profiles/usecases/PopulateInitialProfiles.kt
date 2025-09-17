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

import android.content.Context
import com.protonvpn.android.R
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.profiles.data.ProfileColor
import com.protonvpn.android.profiles.data.ProfileEntity
import com.protonvpn.android.profiles.data.ProfileIcon
import com.protonvpn.android.profiles.data.ProfilesDao
import com.protonvpn.android.profiles.data.profileSettingsOverrides
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.recents.data.toData
import com.protonvpn.android.redesign.settings.ui.NatType
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.ui.storage.UiStateStorage
import com.protonvpn.android.vpn.ProtocolSelection
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import me.proton.core.domain.entity.UserId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PopulateInitialProfiles @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val mainScope: CoroutineScope,
    private val profilesDao: ProfilesDao,
    private val currentUser: CurrentUser,
    private val uiStateStorage: UiStateStorage,
) {
    fun start() {
        currentUser.vpnUserFlow
            .mapNotNull { it?.userId }
            .distinctUntilChanged()
            .onEach { userId ->
                if (!uiStateStorage.state.first().hasPopulatedDefaultProfiles) {
                    profilesDao.prepopulate(userId) {
                        createInitialProfiles(userId)
                    }
                    uiStateStorage.update { it.copy(hasPopulatedDefaultProfiles = true) }
                }
            }
            .launchIn(mainScope)
    }

    private fun createInitialProfiles(userId: UserId): List<ProfileEntity> {
        val now = System.currentTimeMillis()
        val resources = appContext.resources
        return listOf(
            ProfileEntity(
                userId = userId,
                name = resources.getString(R.string.initial_profile_name_streaming_us),
                color = ProfileColor.Color1,
                icon = ProfileIcon.Icon2,
                createdAt = now,
                lastConnectedAt = null,
                isUserCreated = false,
                connectIntentData = ConnectIntent.FastestInCountry(
                    CountryId("US"),
                    emptySet(),
                    settingsOverrides = profileSettingsOverrides(
                        ProtocolSelection(VpnProtocol.WireGuard).toData(),
                    ),
                ).toData(),
                autoOpenText = "",
                autoOpenEnabled = false,
                autoOpenUrlPrivately = false,
            ),
            ProfileEntity(
                userId = userId,
                name = resources.getString(R.string.initial_profile_name_gaming),
                color = ProfileColor.Color1,
                icon = ProfileIcon.Icon7,
                createdAt = now,
                lastConnectedAt = null,
                isUserCreated = false,
                connectIntentData = ConnectIntent.FastestInCountry(
                    CountryId.fastest,
                    emptySet(),
                    settingsOverrides = profileSettingsOverrides(
                        randomizedNat = NatType.Moderate.toRandomizedNat(),
                    )
                ).toData(),
                autoOpenText = "",
                autoOpenEnabled = false,
                autoOpenUrlPrivately = false,
            ),
            ProfileEntity(
                userId = userId,
                name = resources.getString(R.string.initial_profile_name_anti_censorship),
                color = ProfileColor.Color1,
                icon = ProfileIcon.Icon5,
                createdAt = now,
                lastConnectedAt = null,
                isUserCreated = false,
                connectIntentData = ConnectIntent.FastestInCountry(
                    CountryId.fastestExcludingMyCountry,
                    emptySet(),
                    settingsOverrides = profileSettingsOverrides(
                        ProtocolSelection.STEALTH.toData(),
                    )
                ).toData(),
                autoOpenText = "",
                autoOpenEnabled = false,
                autoOpenUrlPrivately = false,
            ),
            ProfileEntity(
                userId = userId,
                name = resources.getString(R.string.initial_profile_name_max_security),
                color = ProfileColor.Color1,
                icon = ProfileIcon.Icon3,
                createdAt = now,
                lastConnectedAt = null,
                isUserCreated = false,
                connectIntentData = ConnectIntent.SecureCore(
                    exitCountry = CountryId.fastest,
                    entryCountry = CountryId.fastest,
                    settingsOverrides = profileSettingsOverrides(
                        lanConnections = false,
                    )
                ).toData(),
                autoOpenText = "",
                autoOpenEnabled = false,
                autoOpenUrlPrivately = false,
            ),
            ProfileEntity(
                userId = userId,
                name = resources.getString(R.string.initial_profile_name_work_school),
                color = ProfileColor.Color1,
                icon = ProfileIcon.Icon9,
                createdAt = now,
                lastConnectedAt = null,
                isUserCreated = false,
                connectIntentData = ConnectIntent.FastestInCountry(
                    CountryId.fastest,
                    emptySet(),
                    settingsOverrides = profileSettingsOverrides(
                        ProtocolSelection.STEALTH.toData(),
                        lanConnections = false,
                    )
                ).toData(),
                autoOpenText = "",
                autoOpenEnabled = false,
                autoOpenUrlPrivately = false,
            ),
        )
    }
}
