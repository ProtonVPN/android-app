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

import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.profiles.data.Profile
import com.protonvpn.android.profiles.ui.SettingsScreenState
import com.protonvpn.android.profiles.ui.TypeAndLocationScreenState
import com.protonvpn.android.redesign.settings.ui.NatType
import com.protonvpn.android.telemetry.CommonDimensions.Companion.NO_VALUE
import com.protonvpn.android.utils.DebugUtils
import com.protonvpn.android.vpn.ProtocolSelection
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
    }

    fun profileCreated(
        typeAndLocationScreen: TypeAndLocationScreenState,
        settingsScreen: SettingsScreenState,
        profileCount: Int
    ) {
        telemetry.event {
            val dimensions: Map<String, String> = buildMap {
                commonDimensions.add(this, CommonDimensions.Key.USER_TIER)
                putDimensions(profileCountBucket(profileCount), Dimen.ProfileCount)
                putAll(profileConfigurationDimensions(typeAndLocationScreen, settingsScreen))
            }
            TelemetryEventData(MEASUREMENT_GROUP, "profile_created", dimensions = dimensions)
        }
    }

    fun profileUpdated(
        typeAndLocationScreen: TypeAndLocationScreenState,
        settingsScreen: SettingsScreenState,
        profile: Profile,
    ) {
        telemetry.event {
            val dimensions = buildMap {
                commonDimensions.add(this, CommonDimensions.Key.USER_TIER)
                putAll(profileConfigurationDimensions(typeAndLocationScreen, settingsScreen))
                putDimensions(userProfileType(profile.info.isUserCreated), Dimen.ProfileType)
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
        settings: SettingsScreenState
    ): Map<String, String> = buildMap {
        putDimensions(typeAndLocation.type.toTelemetry(), Dimen.ConnectionType)

        // Select fields are then overridden depending on intent type.
        putDimensions(NO_VALUE, Dimen.Country, Dimen.EntryCountry, Dimen.CityOrState, Dimen.Server, Dimen.Gateway)
        when (typeAndLocation) {
            is TypeAndLocationScreenState.StandardWithFeatures -> {
                putDimensions(fastestElseSpecific(typeAndLocation.country.id.isFastest), Dimen.Country)
                val cityOrState = typeAndLocation.cityOrState?.let { fastestElseSpecific(it.id.isFastest) } ?: NO_VALUE
                putDimensions(cityOrState, Dimen.CityOrState)
                val server = typeAndLocation.server?.let { fastestElseSpecific(it.isFastest) } ?: NO_VALUE
                putDimensions(server, Dimen.Server)
                putDimensions(NO_VALUE, Dimen.Gateway)
            }
            is TypeAndLocationScreenState.SecureCore -> {
                putDimensions(fastestElseSpecific(typeAndLocation.exitCountry.id.isFastest), Dimen.Country)
                val entryCountry =
                    typeAndLocation.entryCountry?.let { fastestElseSpecific(it.id.isFastest) } ?: NO_VALUE
                putDimensions(entryCountry, Dimen.EntryCountry)
            }
            is TypeAndLocationScreenState.Gateway -> {
                putDimensions(SPECIFIC, Dimen.Gateway)
                putDimensions(fastestElseSpecific(typeAndLocation.server.isFastest), Dimen.Server)
            }
        }

        putDimensions(if (settings.netShield) "f2" else OFF, Dimen.NetShield)
        putDimensions(settings.protocol.toTelemetry(), Dimen.VpnProtocol)
        putDimensions(settings.lanConnections.toOnOff(), Dimen.AllowLan)
        putDimensions(settings.natType.toTelemetry(), Dimen.NatType)
    }

    private fun Boolean.toOnOff() = if (this) ON else OFF

    private fun fastestElseSpecific(isFastest: Boolean) = if (isFastest) FASTEST else SPECIFIC

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

    companion object {
        // Use constants for common strings to avoid typos.
        private const val FASTEST = "fastest"
        private const val SPECIFIC = "specific"
        private const val ON = "on"
        private const val OFF = "off"
    }
}
