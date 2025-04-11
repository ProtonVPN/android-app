/*
 * Copyright (c) 2025. Proton AG
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

package com.protonvpn.test.shared

import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.netshield.NetShieldProtocol
import com.protonvpn.android.redesign.recents.data.ProtocolSelectionData
import com.protonvpn.android.redesign.recents.data.SettingsOverrides
import com.protonvpn.android.settings.data.CustomDnsSettings

fun createSettingsOverrides(
    protocolData: ProtocolSelectionData? = ProtocolSelectionData(VpnProtocol.Smart, null),
    netShield: NetShieldProtocol? = NetShieldProtocol.ENABLED_EXTENDED,
    randomizedNat: Boolean? = true,
    lanConnections: Boolean? = false,
    lanConnectionsAllowDirect: Boolean? = false,
    customDns: CustomDnsSettings? = CustomDnsSettings(toggleEnabled = false)
) = SettingsOverrides(protocolData, netShield, randomizedNat, lanConnections, lanConnectionsAllowDirect, customDns)
