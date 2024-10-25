/*
 * Copyright (c) 2024. Proton AG
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

import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.di.WallClock
import com.protonvpn.android.profiles.data.Profile
import com.protonvpn.android.profiles.data.ProfileInfo
import com.protonvpn.android.profiles.data.ProfilesDao
import com.protonvpn.android.profiles.data.toProfileEntity
import com.protonvpn.android.profiles.ui.NameScreenState
import com.protonvpn.android.profiles.ui.SettingsScreenState
import com.protonvpn.android.profiles.ui.TypeAndLocationScreenState
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.vpn.ConnectIntent
import dagger.Reusable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@Reusable
class CreateOrUpdateProfileFromUi @Inject constructor(
    private val mainScope: CoroutineScope,
    private val profilesDao: ProfilesDao,
    private val currentUser: CurrentUser,
    @WallClock private val wallClock: () -> Long,
) {

    operator fun invoke(
        profileId: Long?,
        creationTime: Long?,
        nameScreen: NameScreenState,
        typeAndLocationScreen: TypeAndLocationScreenState,
        settingsScreen: SettingsScreenState,
    ) {
        mainScope.launch {
            val profile = createProfile(profileId, creationTime, nameScreen, typeAndLocationScreen, settingsScreen)
            if (profile != null) {
                profilesDao.upsert(profile.toProfileEntity())
            }
        }
    }

    private suspend fun createProfile(
        profileId: Long?,
        creationTime: Long?,
        nameScreen: NameScreenState,
        typeAndLocationScreen: TypeAndLocationScreenState,
        settingsScreen: SettingsScreenState,
    ): Profile? {
        val overrides = settingsScreen.toSettingsOverrides()
        val userId = currentUser.user()?.userId ?: return null
        return Profile(
            userId = userId,
            info = ProfileInfo(
                profileId ?: 0L,
                nameScreen.name,
                nameScreen.color,
                nameScreen.icon,
                (typeAndLocationScreen as? TypeAndLocationScreenState.Gateway)?.gateway?.name,
                creationTime ?: wallClock(),
            ),
            connectIntent = when (typeAndLocationScreen) {
                is TypeAndLocationScreenState.P2P,
                is TypeAndLocationScreenState.Standard -> {
                    typeAndLocationScreen as TypeAndLocationScreenState.StandardWithFeatures
                    val country = typeAndLocationScreen.country
                    val serverId = typeAndLocationScreen.server?.id
                    val cityOrState = typeAndLocationScreen.cityOrState
                    val features = typeAndLocationScreen.features
                    when {
                        serverId != null -> {
                            ConnectIntent.Server(
                                serverId = serverId,
                                exitCountry = country.id,
                                features = features,
                                profileId = profileId,
                                settingsOverrides = overrides,
                            )
                        }
                        cityOrState?.id?.isFastest == false && cityOrState.id.isState -> {
                            ConnectIntent.FastestInState(
                                country = country.id,
                                stateEn = cityOrState.id.name,
                                features = features,
                                profileId = profileId,
                                settingsOverrides = overrides,
                            )
                        }
                        cityOrState?.id?.isFastest == false && !cityOrState.id.isState -> {
                            ConnectIntent.FastestInCity(
                                country = country.id,
                                cityEn = cityOrState.id.name,
                                features = features,
                                profileId = profileId,
                                settingsOverrides = overrides,
                            )
                        }
                        else -> {
                            ConnectIntent.FastestInCountry(
                                country = country.id,
                                features = features,
                                profileId = profileId,
                                settingsOverrides = overrides,
                            )
                        }
                    }
                }
                is TypeAndLocationScreenState.SecureCore -> ConnectIntent.SecureCore(
                    exitCountry = typeAndLocationScreen.exitCountry.id,
                    entryCountry = typeAndLocationScreen.entryCountry?.id ?: CountryId.fastest,
                    profileId = profileId,
                    settingsOverrides = overrides,
                )
                is TypeAndLocationScreenState.Gateway -> ConnectIntent.Gateway(
                    gatewayName = typeAndLocationScreen.gateway.name,
                    profileId = profileId,
                    serverId = typeAndLocationScreen.server.id,
                    settingsOverrides = overrides,
                )
            }
        )
    }
}
