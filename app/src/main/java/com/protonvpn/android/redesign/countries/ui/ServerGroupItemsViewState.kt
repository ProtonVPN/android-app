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
package com.protonvpn.android.redesign.countries.ui

import androidx.annotation.StringRes
import com.protonvpn.android.redesign.CityStateId
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.ServerId
import com.protonvpn.android.redesign.base.ui.InfoType
import com.protonvpn.android.redesign.vpn.ServerFeature

// Server data as needed by the UI, doesn't have dynamic properties like isConnected or
// isAvailableForCurrentUser, those will be calculated by view model as ItemState
sealed class ServerGroupItemData {
    abstract val countryId: CountryId?
    abstract val inMaintenance: Boolean
    abstract val tier: Int

    data class Country(
        override val countryId: CountryId,
        override val inMaintenance: Boolean,
        override val tier: Int,
        val entryCountryId: CountryId?,
    ) : ServerGroupItemData()

    data class City(
        override val countryId: CountryId,
        override val inMaintenance: Boolean,
        override val tier: Int,
        val cityStateId: CityStateId,
        val name: String,
    ) : ServerGroupItemData()

    data class Server(
        override val countryId: CountryId,
        override val inMaintenance: Boolean,
        override val tier: Int,
        val entryCountryId: CountryId?,
        val gatewayName: String?,
        val serverId: ServerId,
        val name: String,
        val loadPercent: Int,
        val serverFeatures: Set<ServerFeature>,
        val isVirtualLocation: Boolean,
    ) : ServerGroupItemData()

    data class Gateway(
        override val inMaintenance: Boolean,
        override val tier: Int,
        val gatewayName: String,
    ) : ServerGroupItemData() {
        override val countryId: CountryId? get() = null
    }
}

sealed class ServerGroupUiItem {

    data class ServerGroup(
        val data: ServerGroupItemData,
        val available: Boolean,
        val connected: Boolean,
    ) : ServerGroupUiItem()

    data class Header(
        @StringRes val labelRes: Int,
        val count: Int,
        val info: InfoType?,
    ) : ServerGroupUiItem()

    enum class BannerType { Countries, SecureCore, P2P, Tor }
    data class Banner(
        val type: BannerType,
    ) : ServerGroupUiItem()
}
