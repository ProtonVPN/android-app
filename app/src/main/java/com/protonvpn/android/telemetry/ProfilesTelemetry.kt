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

package com.protonvpn.android.telemetry

import com.protonvpn.android.profiles.data.Profile
import com.protonvpn.android.profiles.data.ProfileAutoOpen
import com.protonvpn.android.profiles.ui.SettingsScreenState
import com.protonvpn.android.profiles.ui.TypeAndLocationScreenState
import com.protonvpn.android.profiles.usecases.PrivateBrowsingAvailability
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.telemetry.CommonDimensions.Companion.NO_VALUE
import com.protonvpn.android.utils.DebugUtils
import dagger.Reusable
import javax.inject.Inject

private const val MEASUREMENT_GROUP = "vpn.profiles"

@Reusable
class ProfilesTelemetry @Inject constructor(
    private val commonDimensions: CommonDimensions,
    private val telemetry: TelemetryFlowHelper,
) {
    private enum class Dimen(val telemetryKey: String) {
        ConnectionType("vpn_connection_type"),
        ProfileCount("profile_count"),
        ProfileType("profile_type"),
        SourceProfileType("source_profile_type"),
        Country("country_selection_type"),
        EntryCountry("entry_country_selection_type"),
        CityOrState("city_or_state_selection_type"),
        Gateway("gateway_selection_type"),
        Server("server_selection_type"),
        NetShield("netshield_setting"),
        VpnProtocol("vpn_protocol"),
        NatType("nat_type"),
        AllowLan("lan_access"),
        EditSource("edit_route_source"),
        AutoOpen("auto_open"),
        AutoOpenPrivateModeAvailability("auto_open_private_mode_availability"),
    }

    fun profileCreated(
        typeAndLocationScreen: TypeAndLocationScreenState,
        settingsScreen: SettingsScreenState,
        profileCount: Int,
        privateBrowsingAvailability: PrivateBrowsingAvailability
    ) {
        telemetry.event {
            val dimensions: Map<String, String> = buildMap {
                commonDimensions.add(this, CommonDimensions.Key.USER_TIER)
                putDimensions(profileCountBucket(profileCount), Dimen.ProfileCount)
                putAll(profileConfigurationDimensions(typeAndLocationScreen, settingsScreen, privateBrowsingAvailability))
            }
            TelemetryEventData(MEASUREMENT_GROUP, "profile_created", dimensions = dimensions)
        }
    }

    fun profileDuplicated(
        typeAndLocationScreen: TypeAndLocationScreenState,
        settingsScreen: SettingsScreenState,
        isSourceProfileUserCreated: Boolean,
        profileCount: Int,
        privateBrowsingAvailability: PrivateBrowsingAvailability
    ) {
        telemetry.event {
            val dimensions: Map<String, String> = buildMap {
                commonDimensions.add(this, CommonDimensions.Key.USER_TIER)
                putDimensions(profileCountBucket(profileCount), Dimen.ProfileCount)
                putDimensions(userProfileType(isSourceProfileUserCreated), Dimen.SourceProfileType)
                putAll(profileConfigurationDimensions(typeAndLocationScreen, settingsScreen, privateBrowsingAvailability))
            }
            TelemetryEventData(MEASUREMENT_GROUP, "profile_duplicated", dimensions = dimensions)
        }
    }

    fun profileUpdated(
        typeAndLocationScreen: TypeAndLocationScreenState,
        settingsScreen: SettingsScreenState,
        profile: Profile,
        routedFromSettings: Boolean,
        privateBrowsingAvailability: PrivateBrowsingAvailability
    ) {
        telemetry.event {
            val dimensions = buildMap {
                commonDimensions.add(this, CommonDimensions.Key.USER_TIER)
                putAll(profileConfigurationDimensions(typeAndLocationScreen, settingsScreen, privateBrowsingAvailability))
                putDimensions(userProfileType(profile.info.isUserCreated), Dimen.ProfileType)
                putDimensions(profileEditSource(routedFromSettings), Dimen.EditSource)
            }
            TelemetryEventData(MEASUREMENT_GROUP, "profile_updated", dimensions = dimensions)
        }
    }

    fun profileDeleted(profile: Profile, profileCount: Int) {
        telemetry.event {
            val dimensions = buildMap {
                commonDimensions.add(this, CommonDimensions.Key.USER_TIER)
                putDimensions(profileCountBucket(profileCount), Dimen.ProfileCount)
                putDimensions(userProfileType(profile.info.isUserCreated), Dimen.ProfileType)
            }
            TelemetryEventData(MEASUREMENT_GROUP, "profile_deleted", dimensions = dimensions)
        }
    }

    private fun MutableMap<String, String>.putDimensions(value: String?, dimension: Dimen, vararg dimensions: Dimen) {
        if (value != null) {
            put(dimension.telemetryKey, value)
            dimensions.forEach { put(it.telemetryKey, value) }
        }
    }

    private fun profileConfigurationDimensions(
        typeAndLocation: TypeAndLocationScreenState,
        settings: SettingsScreenState,
        privateBrowsingAvailability: PrivateBrowsingAvailability
    ): Map<String, String> = buildMap {
        putDimensions(typeAndLocation.type.toTelemetry(), Dimen.ConnectionType)

        // Select fields are then overridden depending on intent type.
        putDimensions(NO_VALUE, Dimen.Country, Dimen.EntryCountry, Dimen.CityOrState, Dimen.Server, Dimen.Gateway)
        when (typeAndLocation) {
            is TypeAndLocationScreenState.StandardWithFeatures -> {
                putDimensions(typeAndLocation.country.toTelemetry(), Dimen.Country)
                putDimensions(typeAndLocation.cityOrState.toTelemetry(), Dimen.CityOrState)
                putDimensions(typeAndLocation.server.toTelemetry(), Dimen.Server)
                putDimensions(NO_VALUE, Dimen.Gateway)
            }
            is TypeAndLocationScreenState.SecureCore -> {
                putDimensions(typeAndLocation.exitCountry.toTelemetry(), Dimen.Country)
                putDimensions(typeAndLocation.entryCountry.toTelemetry(), Dimen.EntryCountry)
            }
            is TypeAndLocationScreenState.Gateway -> {
                putDimensions(SPECIFIC, Dimen.Gateway)
                putDimensions(typeAndLocation.server.toTelemetry(), Dimen.Server)
            }
        }

        val netShield = when (settings.netShield) {
            null -> NO_VALUE
            true -> "f2"
            false -> OFF
        }
        putDimensions(netShield, Dimen.NetShield)
        putDimensions(settings.protocol.toTelemetry(), Dimen.VpnProtocol)
        putDimensions(settings.lanConnections.toOnOff(), Dimen.AllowLan)
        putDimensions(settings.natType.toTelemetry(), Dimen.NatType)
        putDimensions(settings.autoOpen.toTelemetry(), Dimen.AutoOpen)
        val autoOpenInPrivateEnabled = settings.autoOpen is ProfileAutoOpen.Url && settings.autoOpen.openInPrivateMode
        putDimensions(
            if (autoOpenInPrivateEnabled) privateBrowsingAvailability.toTelemetry() else NO_VALUE,
            Dimen.AutoOpenPrivateModeAvailability
        )
    }

    private fun Boolean.toOnOff() = if (this) ON else OFF

    private fun profileEditSource(routedFromSettings: Boolean) = if (routedFromSettings) "global_settings_route" else "normal_route"

    private fun userProfileType(isUserProfile: Boolean) = if (isUserProfile) "user_created" else "pre_made"

    private fun profileCountBucket(profileCount: Int): String {
        DebugUtils.debugAssert { profileCount >= 0 }
        return when {
            profileCount < 5 -> profileCount.toString()
            profileCount < 10 -> "5-9"
            profileCount < 50 -> "10-49"
            else -> ">=50"
        }
    }

    private fun TypeAndLocationScreenState.CountryItem?.toTelemetry() = when {
        this == null -> NO_VALUE
        id == CountryId.fastest -> FASTEST
        id == CountryId.fastestExcludingMyCountry -> FASTEST_EXCLUDING_MINE
        else -> SPECIFIC
    }

    private fun TypeAndLocationScreenState.ServerItem?.toTelemetry() = when {
        this == null -> NO_VALUE
        isFastest -> FASTEST
        else -> SPECIFIC
    }

    private fun TypeAndLocationScreenState.CityOrStateItem?.toTelemetry() = when {
        this == null -> NO_VALUE
        id.isFastest -> FASTEST
        else -> SPECIFIC
    }

    companion object {
        // Use constants for common strings to avoid typos.
        private const val FASTEST = "fastest"
        private const val FASTEST_EXCLUDING_MINE = "fastest_excluding_mine"
        private const val SPECIFIC = "specific"
        private const val ON = "on"
        private const val OFF = "off"
    }
}
