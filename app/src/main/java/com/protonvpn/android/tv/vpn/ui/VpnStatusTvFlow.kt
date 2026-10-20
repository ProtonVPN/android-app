/*
 * Copyright (c) 2026. Proton AG
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

package com.protonvpn.android.tv.vpn.ui

import com.protonvpn.android.redesign.countries.Translator
import com.protonvpn.android.redesign.countries.city
import com.protonvpn.android.redesign.countries.state
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.servers.Server
import com.protonvpn.android.ui.home.ServerListUpdaterPrefs
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.VpnStatusProviderUI
import dagger.Reusable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import me.proton.core.util.kotlin.takeIfNotEmpty
import java.util.Locale
import javax.inject.Inject

data class VpnStatusTvViewState(
    val status: Status,
    val location: Location,
) {
    enum class Status { Unprotected, Connecting, Protected }

    data class Location(val label: String?, val ip: String?)
}

@Reusable
class VpnStatusTvFlow @Inject constructor(
    private val vpnStatusProviderUI: VpnStatusProviderUI,
    private val vpnStateMonitor: VpnStateMonitor,
    private val serverListUpdaterPrefs: ServerListUpdaterPrefs,
    private val translator: Translator,
) {
    // Emits null while the VPN state is loading.
    fun observeStatus(): Flow<VpnStatusTvViewState.Status?> = vpnStatusProviderUI.uiStatus
        .map { it.state.toStatus() }
        .distinctUntilChanged()

    // Emits null while the VPN state is loading.
    fun observe(locale: Locale): Flow<VpnStatusTvViewState?> = combine(
        vpnStatusProviderUI.uiStatus,
        serverListUpdaterPrefs.lastKnownCountryFlow,
        serverListUpdaterPrefs.ipAddressFlow,
        vpnStateMonitor.exitIp,
        translator.flow,
    ) { vpnStatus, physicalCountry, physicalIp, exitIp, translations ->
        val status = vpnStatus.state.toStatus() ?: return@combine null
        val location = when (status) {
            VpnStatusTvViewState.Status.Unprotected, VpnStatusTvViewState.Status.Connecting ->
                physicalLocation(physicalCountry, physicalIp, locale)
            VpnStatusTvViewState.Status.Protected -> {
                val label = vpnStatus.server?.let { server ->
                    // The city or state is shown only when the user chose to connect to it.
                    val region = when (vpnStatus.connectIntent) {
                        is ConnectIntent.FastestInCity -> translations.city(server)
                        is ConnectIntent.FastestInState -> translations.state(server)
                        else -> null
                    }
                    server.countryAndRegion(region, locale)
                }
                VpnStatusTvViewState.Location(label, exitIp?.ipV4)
            }
        }
        VpnStatusTvViewState(status, location)
    }.distinctUntilChanged()

    private fun VpnState.toStatus(): VpnStatusTvViewState.Status? = when {
        this == VpnState.Loading -> null
        this == VpnState.Connected -> VpnStatusTvViewState.Status.Protected
        // Includes waiting for network and non-final errors.
        isEstablishingConnection -> VpnStatusTvViewState.Status.Connecting
        else -> VpnStatusTvViewState.Status.Unprotected
    }

    private fun physicalLocation(country: String, ip: String, locale: Locale) = VpnStatusTvViewState.Location(
        country.takeIfNotEmpty()?.let { CountryTools.getFullName(locale, country) },
        ip.takeIfNotEmpty()
    )

    private fun Server.countryAndRegion(region: String?, locale: Locale): String {
        val country = CountryTools.getFullName(locale, exitCountry)
        return if (region.isNullOrEmpty()) country else "$country - $region"
    }
}
