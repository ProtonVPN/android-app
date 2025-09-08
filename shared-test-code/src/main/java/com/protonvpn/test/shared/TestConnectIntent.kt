package com.protonvpn.test.shared

import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.recents.data.SettingsOverrides
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.ServerFeature

fun createConnectIntentFastest(): ConnectIntent = ConnectIntent.Fastest

fun createConnectIntentFastestInCountry(
    country: CountryId = CountryId.fastest,
    features: Set<ServerFeature> = emptySet(),
    profileId: Long? = null,
    settingsOverrides: SettingsOverrides? = null,
): ConnectIntent = ConnectIntent.FastestInCountry(
    country = country,
    features = features,
    profileId = profileId,
    settingsOverrides = settingsOverrides,
)

fun createConnectIntentGateway(
    gatewayName: String = "TestGateway",
    serverId: String = "TestGatewayId",
    profileId: Long? = null,
    settingsOverrides: SettingsOverrides? = null,
): ConnectIntent = ConnectIntent.Gateway(
    gatewayName = gatewayName,
    serverId = serverId,
    profileId = profileId,
    settingsOverrides = settingsOverrides,
)
