/*
 * Copyright (c) 2021. Proton Technologies AG
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

package com.protonvpn.android.ui

import android.os.Parcelable
import androidx.annotation.StringRes
import com.protonvpn.android.R
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.VpnProtocol
import kotlinx.android.parcel.IgnoredOnParcel
import kotlinx.android.parcel.Parcelize

@Parcelize
data class ProtocolSelection(
    val protocol: VpnProtocol,
    val transmissionProtocol: TransmissionProtocol
) : Parcelable {

    @IgnoredOnParcel
    @StringRes
    val displayName: Int = when(protocol) {
        VpnProtocol.Smart -> R.string.settingsProtocolNameSmart
        VpnProtocol.IKEv2 -> R.string.settingsProtocolNameIkeV2
        VpnProtocol.WireGuard -> R.string.settingsProtocolNameWireguard
        VpnProtocol.OpenVPN -> when (transmissionProtocol) {
            TransmissionProtocol.TCP -> R.string.settingsProtocolNameOpenVpnTcp
            TransmissionProtocol.UDP -> R.string.settingsProtocolNameOpenVpnUdp
        }
    }
}
