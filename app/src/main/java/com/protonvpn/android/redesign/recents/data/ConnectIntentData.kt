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

package com.protonvpn.android.redesign.recents.data

import androidx.room.Embedded
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.netshield.NetShieldProtocol
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.vpn.AnyConnectIntent
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.ServerFeature
import com.protonvpn.android.settings.data.CustomDnsSettings
import com.protonvpn.android.vpn.ProtocolSelection
import me.proton.core.util.kotlin.takeIfNotBlank

enum class ConnectIntentType {
    FASTEST,
    FASTEST_IN_CITY,
    FASTEST_IN_REGION,
    SECURE_CORE,
    GATEWAY,
    SPECIFIC_SERVER,
    GUEST_HOLE
}

// Note: when adding new fields add them to RecentDao.updateConnectionTimestamp.
data class ConnectIntentData(
    val connectIntentType: ConnectIntentType,
    val exitCountry: String?,
    val entryCountry: String?,
    val city: String?,
    val region: String?,
    val gatewayName: String?,
    val serverId: String?,
    val features: Set<ServerFeature>,
    val profileId: Long?,
    @Embedded val settingsOverrides: SettingsOverrides?
) : java.io.Serializable

data class SettingsOverrides(
    @Embedded val protocolData: ProtocolSelectionData?,
    val netShield: NetShieldProtocol?,
    val randomizedNat: Boolean?,
    val lanConnections: Boolean?,
    val lanConnectionsAllowDirect: Boolean?,
    @Embedded val customDns: CustomDnsSettings?
): java.io.Serializable {
    val protocol get() = protocolData?.toProtocolSelection()
}

data class ProtocolSelectionData(
    val vpn: VpnProtocol,
    val transmission: TransmissionProtocol?
): java.io.Serializable {
    fun toProtocolSelection(): ProtocolSelection = ProtocolSelection(vpn, transmission)
}

fun ProtocolSelection.toData() = ProtocolSelectionData(vpn, transmission)

fun ConnectIntentData.toConnectIntent(): ConnectIntent =
    when (connectIntentType) {
        ConnectIntentType.FASTEST ->
            ConnectIntent.FastestInCountry(
                country = exitCountry.toCountryId(),
                features = features,
                profileId = profileId,
                settingsOverrides = settingsOverrides,
            )

        ConnectIntentType.FASTEST_IN_CITY ->
            ConnectIntent.FastestInCity(
                country = exitCountry.toCountryId(),
                cityEn = requireNotNull(city),
                features = features,
                profileId = profileId,
                settingsOverrides = settingsOverrides,
            )

        ConnectIntentType.FASTEST_IN_REGION ->
            ConnectIntent.FastestInState(
                country = exitCountry.toCountryId(),
                stateEn = requireNotNull(region),
                features = features,
                profileId = profileId,
                settingsOverrides = settingsOverrides,
            )

        ConnectIntentType.SECURE_CORE ->
            ConnectIntent.SecureCore(
                exitCountry = exitCountry.toCountryId(),
                entryCountry = entryCountry.toCountryId(),
                profileId = profileId,
                settingsOverrides = settingsOverrides,
            )

        ConnectIntentType.GATEWAY ->
            ConnectIntent.Gateway(
                gatewayName = gatewayName!!,
                serverId = serverId,
                profileId = profileId,
                settingsOverrides = settingsOverrides,
            )

        ConnectIntentType.SPECIFIC_SERVER ->
            ConnectIntent.Server(
                serverId = requireNotNull(serverId),
                exitCountry = exitCountry?.let { CountryId(it) },
                features = features,
                profileId = profileId,
                settingsOverrides = settingsOverrides,
            )
        ConnectIntentType.GUEST_HOLE -> throw IllegalArgumentException("GUEST_HOLE is not a regular ConnectIntent type")
    }

fun ConnectIntentData.toAnyConnectIntent(): AnyConnectIntent = when(connectIntentType) {
    ConnectIntentType.GUEST_HOLE -> AnyConnectIntent.GuestHole(requireNotNull(serverId))
    else -> this.toConnectIntent()
}

fun ConnectIntent.toData(): ConnectIntentData =
    when(this) {
        is ConnectIntent.FastestInCountry ->
            ConnectIntentData(
                connectIntentType = ConnectIntentType.FASTEST,
                exitCountry = country.toDataString(),
                entryCountry = null,
                city = null,
                gatewayName = null,
                serverId = null,
                features = features,
                region = null,
                profileId = profileId,
                settingsOverrides = settingsOverrides,
            )
        is ConnectIntent.FastestInCity ->
            ConnectIntentData(
                connectIntentType = ConnectIntentType.FASTEST_IN_CITY,
                exitCountry = country.toDataString(),
                entryCountry = null,
                city = cityEn,
                gatewayName = null,
                serverId = null,
                features = features,
                region = null,
                profileId = profileId,
                settingsOverrides = settingsOverrides,
            )
        is ConnectIntent.FastestInState ->
            ConnectIntentData(
                connectIntentType = ConnectIntentType.FASTEST_IN_REGION,
                exitCountry = country.toDataString(),
                entryCountry = null,
                city = null,
                gatewayName = null,
                serverId = null,
                features = features,
                region = stateEn,
                profileId = profileId,
                settingsOverrides = settingsOverrides,
            )
        is ConnectIntent.SecureCore ->
            ConnectIntentData(
                connectIntentType = ConnectIntentType.SECURE_CORE,
                exitCountry = exitCountry.toDataString(),
                entryCountry = entryCountry.toDataString(),
                city = null,
                gatewayName = null,
                serverId = null,
                features = features,
                region = null,
                profileId = profileId,
                settingsOverrides = settingsOverrides,
            )
        is ConnectIntent.Gateway ->
            ConnectIntentData(
                connectIntentType = ConnectIntentType.GATEWAY,
                exitCountry = null,
                entryCountry = null,
                city = null,
                gatewayName = gatewayName,
                serverId = serverId,
                features = features,
                region = null,
                profileId = profileId,
                settingsOverrides = settingsOverrides,
            )
        is ConnectIntent.Server ->
            ConnectIntentData(
                connectIntentType = ConnectIntentType.SPECIFIC_SERVER,
                exitCountry = exitCountry?.toDataString(),
                entryCountry = null,
                city = null,
                gatewayName = null,
                serverId = serverId,
                features = features,
                region = null,
                profileId = profileId,
                settingsOverrides = settingsOverrides,
            )
    }

fun AnyConnectIntent.toData() = when (this) {
    is AnyConnectIntent.GuestHole -> ConnectIntentData(
        connectIntentType = ConnectIntentType.GUEST_HOLE,
        exitCountry = null,
        entryCountry = null,
        city = null,
        serverId = serverId,
        gatewayName = null,
        features = emptySet(),
        region = null,
        profileId = profileId,
        settingsOverrides = settingsOverrides,
    )
    is ConnectIntent -> this.toData()
}

private fun CountryId.toDataString(): String? = countryCode.takeIfNotBlank()
private fun String?.toCountryId(): CountryId = if (this == null) CountryId.fastest else CountryId(this)
