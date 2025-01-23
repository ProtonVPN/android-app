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

// This set of values is provided by the data team.
private enum class ConnectStatsKeyword {
    CONNECTION_CARD,
    CHANGE_SERVER,
    QUICK,
    COUNTRIES_COUNTRY,
    COUNTRIES_STATE,
    COUNTRIES_CITY,
    COUNTRIES_SERVER,
    GATEWAYS_GATEWAY,
    GATEWAYS_SERVER,
    SEARCH_COUNTRY,
    SEARCH_STATE,
    SEARCH_CITY,
    SEARCH_SERVER,
    PROFILE,
    MAP,
    TRAY,
    AUTO,
    RECENT,
    PIN,
    WIDGET
}

private enum class DisconnectStatsKeyword {
    CONNECTION_CARD, QUICK, COUNTRY, SERVER, PROFILE, MAP, TRAY, AUTO, NEW_CONNECTION, WIDGET
}

sealed class ConnectTrigger(statsKeyword: ConnectStatsKeyword, val description: String) {
    object ConnectionCard : ConnectTrigger(ConnectStatsKeyword.CONNECTION_CARD, "connection card")
    object ChangeServer : ConnectTrigger(ConnectStatsKeyword.CHANGE_SERVER, "change server")
    class QuickConnect(description: String) : ConnectTrigger(ConnectStatsKeyword.QUICK, description)
    object RecentRegular : ConnectTrigger(ConnectStatsKeyword.RECENT, "recent")
    object RecentPinned : ConnectTrigger(ConnectStatsKeyword.PIN, "pinned recent")
    object Profile : ConnectTrigger(ConnectStatsKeyword.PROFILE, "profile")
    class Onboarding(description: String) : ConnectTrigger(ConnectStatsKeyword.QUICK, description)
    class CountriesCountry(description: String) : ConnectTrigger(ConnectStatsKeyword.COUNTRIES_COUNTRY, description)
    class CountriesState(description: String) : ConnectTrigger(ConnectStatsKeyword.COUNTRIES_STATE, description)
    class CountriesCity(description: String) : ConnectTrigger(ConnectStatsKeyword.COUNTRIES_CITY, description)
    class CountriesServer(description: String) : ConnectTrigger(ConnectStatsKeyword.COUNTRIES_SERVER, description)
    class GatewaysGateway(description: String) : ConnectTrigger(ConnectStatsKeyword.GATEWAYS_GATEWAY, description)
    class GatewaysServer(description: String) : ConnectTrigger(ConnectStatsKeyword.GATEWAYS_SERVER, description)
    class SearchCountry(description: String) : ConnectTrigger(ConnectStatsKeyword.SEARCH_COUNTRY, description)
    class SearchState(description: String) : ConnectTrigger(ConnectStatsKeyword.SEARCH_STATE, description)
    class SearchCity(description: String) : ConnectTrigger(ConnectStatsKeyword.SEARCH_CITY, description)
    class SearchServer(description: String) : ConnectTrigger(ConnectStatsKeyword.SEARCH_SERVER, description)
    object QuickTile : ConnectTrigger(ConnectStatsKeyword.TRAY, "quick tile")
    class Notification(description: String) : ConnectTrigger(ConnectStatsKeyword.TRAY, description)
    class Auto(description: String) : ConnectTrigger(ConnectStatsKeyword.AUTO, description)
    object GuestHole : ConnectTrigger(ConnectStatsKeyword.AUTO, "guest hole")
    object Reconnect : ConnectTrigger(ConnectStatsKeyword.AUTO, "reconnection")
    class Widget(description: String) : ConnectTrigger(ConnectStatsKeyword.WIDGET, description)
    // Fallback doesn't send an abort event for connection being established, instead it preserves the original trigger.
    class Fallback(description: String) : ConnectTrigger(ConnectStatsKeyword.AUTO, description)

    val statsName = statsKeyword.name.lowercase()
}

sealed class DisconnectTrigger(
    statsKeyword: DisconnectStatsKeyword,
    val description: String,
    val isSuccess: Boolean = true
) {
    object ConnectionCard : DisconnectTrigger(DisconnectStatsKeyword.CONNECTION_CARD, "connection card")
    class QuickConnect(description: String) : DisconnectTrigger(DisconnectStatsKeyword.QUICK, description)
    class Country(description: String) : DisconnectTrigger(DisconnectStatsKeyword.COUNTRY, description)
    class Server(description: String) : DisconnectTrigger(DisconnectStatsKeyword.SERVER, description)
    class Search(description: String) : DisconnectTrigger(DisconnectStatsKeyword.SERVER, description)
    object QuickTile : DisconnectTrigger(DisconnectStatsKeyword.TRAY, "quick tile")
    class Notification(description: String) : DisconnectTrigger(DisconnectStatsKeyword.TRAY, description)
    object Logout : DisconnectTrigger(DisconnectStatsKeyword.AUTO, "log out")
    class Error(description: String) : DisconnectTrigger(DisconnectStatsKeyword.AUTO, description, isSuccess = false)
    object GuestHole : DisconnectTrigger(DisconnectStatsKeyword.AUTO, "guest hole")
    class Reconnect(description: String) : DisconnectTrigger(DisconnectStatsKeyword.NEW_CONNECTION, description)
    object NewConnection : DisconnectTrigger(DisconnectStatsKeyword.NEW_CONNECTION, "new connection")
    object Fallback : DisconnectTrigger(DisconnectStatsKeyword.AUTO, "fallback")
    class Onboarding(description: String) : DisconnectTrigger(DisconnectStatsKeyword.QUICK, description)
    object ServiceDestroyed : DisconnectTrigger(DisconnectStatsKeyword.AUTO, "VPN service destroyed")
    class Test(description: String = "test") : DisconnectTrigger(DisconnectStatsKeyword.AUTO, description)
    object Widget : DisconnectTrigger(DisconnectStatsKeyword.WIDGET, "widget")

    val statsName = statsKeyword.name.lowercase()
}
