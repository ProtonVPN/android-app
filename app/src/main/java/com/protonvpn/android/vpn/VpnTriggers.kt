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
    QUICK, COUNTRY, SERVER, PROFILE, MAP, TRAY, AUTO
}

private enum class DisconnectStatsKeyword {
    QUICK, COUNTRY, SERVER, PROFILE, MAP, TRAY, AUTO, NEW_CONNECTION
}

sealed class ConnectTrigger(statsKeyword: ConnectStatsKeyword, val description: String) {
    class QuickConnect(description: String) : ConnectTrigger(ConnectStatsKeyword.QUICK, description)
    class ConnectionPanel(description: String) : ConnectTrigger(ConnectStatsKeyword.QUICK, description)
    class Onboarding(description: String) : ConnectTrigger(ConnectStatsKeyword.QUICK, description)
    class Country(description: String) : ConnectTrigger(ConnectStatsKeyword.COUNTRY, description)
    class Server(description: String) : ConnectTrigger(ConnectStatsKeyword.SERVER, description)
    class Search(description: String) : ConnectTrigger(ConnectStatsKeyword.SERVER, description)
    class Profile(description: String) : ConnectTrigger(ConnectStatsKeyword.PROFILE, description)
    object Map : ConnectTrigger(ConnectStatsKeyword.MAP, "map marker")
    object QuickTile : ConnectTrigger(ConnectStatsKeyword.TRAY, "quick tile")
    class Notification(description: String) : ConnectTrigger(ConnectStatsKeyword.TRAY, description)
    class Auto(description: String) : ConnectTrigger(ConnectStatsKeyword.AUTO, description)
    object GuestHole : ConnectTrigger(ConnectStatsKeyword.AUTO, "guest hole")
    object Reconnect : ConnectTrigger(ConnectStatsKeyword.AUTO, "reconnection")
    object SecureCore : ConnectTrigger(ConnectStatsKeyword.AUTO, "Secure Core switch")
    // Fallback doesn't send an abort event for connection being established, instead it preserves the original trigger.
    class Fallback(description: String) : ConnectTrigger(ConnectStatsKeyword.AUTO, description)

    val statsName = statsKeyword.name.lowercase()
}

sealed class DisconnectTrigger(
    statsKeyword: DisconnectStatsKeyword,
    val description: String,
    val isSuccess: Boolean = true
) {
    class QuickConnect(description: String) : DisconnectTrigger(DisconnectStatsKeyword.QUICK, description)
    class ConnectionPanel(description: String) : DisconnectTrigger(DisconnectStatsKeyword.QUICK, description)
    class Country(description: String) : DisconnectTrigger(DisconnectStatsKeyword.COUNTRY, description)
    class Server(description: String) : DisconnectTrigger(DisconnectStatsKeyword.SERVER, description)
    class Search(description: String) : DisconnectTrigger(DisconnectStatsKeyword.SERVER, description)
    class Profile(description: String) : DisconnectTrigger(DisconnectStatsKeyword.PROFILE, description)
    object Map : DisconnectTrigger(DisconnectStatsKeyword.MAP, "map marker")
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

    val statsName = statsKeyword.name.lowercase()
}
