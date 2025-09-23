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
import com.protonvpn.android.telemetry.ProfilesTelemetry
import dagger.Reusable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import me.proton.core.domain.entity.UserId
import javax.inject.Inject

@Reusable
class CreateOrUpdateProfileFromUi @Inject constructor(
    private val mainScope: CoroutineScope,
    private val profilesDao: ProfilesDao,
    private val currentUser: CurrentUser,
    private val telemetry: ProfilesTelemetry,
    private val getPrivateBrowsingAvailability: GetPrivateBrowsingAvailability,
    @WallClock private val wallClock: () -> Long,
) {

    operator fun invoke(
        profileId: Long?,
        createdAt: Long?,
        nameScreen: NameScreenState,
        typeAndLocationScreen: TypeAndLocationScreenState,
        settingsScreen: SettingsScreenState,
        createDuplicate: Boolean = false,
        routedFromSettings: Boolean = false,
    ) : Deferred<Profile?> = mainScope.async {
        val privateBrowsingAvailability = getPrivateBrowsingAvailability()
        currentUser.vpnUser()?.userId?.let { userId ->
            val existingProfile =
                if (profileId == null) null else profilesDao.getProfileById(profileId)
            val isUserCreated =
                createDuplicate || isUserCreated(
                    existingProfile,
                    nameScreen,
                    typeAndLocationScreen,
                    settingsScreen
                )
            val profile = createProfile(
                userId,
                profileId.takeUnless { createDuplicate },
                isUserCreated,
                createdAt,
                nameScreen,
                typeAndLocationScreen,
                settingsScreen
            )
            profilesDao.upsert(profile.toProfileEntity())
            if (existingProfile == null || createDuplicate) {
                // Profile count should include the new profile.
                val profileCount = profilesDao.getProfileCount(userId)
                if (createDuplicate && existingProfile != null) {
                    val isSourceUserCreated = existingProfile.info.isUserCreated
                    telemetry.profileDuplicated(
                        typeAndLocationScreen,
                        settingsScreen,
                        isSourceUserCreated,
                        profileCount,
                        privateBrowsingAvailability
                    )
                } else {
                    telemetry.profileCreated(
                        typeAndLocationScreen,
                        settingsScreen,
                        profileCount,
                        privateBrowsingAvailability
                    )
                }
            } else {
                telemetry.profileUpdated(typeAndLocationScreen, settingsScreen, existingProfile, routedFromSettings, privateBrowsingAvailability)
            }
            profile
        }
    }

    private fun isUserCreated(
        existingProfile: Profile?,
        nameScreen: NameScreenState,
        typeAndLocationScreen: TypeAndLocationScreenState,
        settingsScreen: SettingsScreenState
    ): Boolean {
        if (existingProfile == null || existingProfile.info.isUserCreated) return true

        val newProfile = with(existingProfile.info) {
            createProfile(
                existingProfile.userId,
                id,
                isUserCreated,
                createdAt,
                nameScreen,
                typeAndLocationScreen,
                settingsScreen
            )
        }
        // If changes were made then the profile becomes user-created.
        return newProfile != existingProfile
    }

    fun applyEditsToProfile(
        profile: Profile,
        nameScreen: NameScreenState,
        typeAndLocationScreen: TypeAndLocationScreenState,
        settingsScreen: SettingsScreenState
    ): Profile {
        val isUserCreated = profile.info.isUserCreated
        val profileId = profile.info.id
        val userId = profile.userId
        val creationTime = profile.info.createdAt
        return createProfile(
            userId,
            profileId,
            isUserCreated,
            creationTime,
            nameScreen,
            typeAndLocationScreen,
            settingsScreen
        )
    }

    private fun createProfile(
        userId: UserId,
        profileId: Long?,
        isUserCreated: Boolean,
        creationTime: Long?,
        nameScreen: NameScreenState,
        typeAndLocationScreen: TypeAndLocationScreenState,
        settingsScreen: SettingsScreenState,
    ): Profile {
        val overrides = settingsScreen.toSettingsOverrides()
        return Profile(
            userId = userId,
            autoOpen = settingsScreen.autoOpen,
            info = ProfileInfo(
                profileId ?: 0L,
                nameScreen.name,
                nameScreen.color,
                nameScreen.icon,
                creationTime ?: wallClock(),
                isUserCreated,
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
