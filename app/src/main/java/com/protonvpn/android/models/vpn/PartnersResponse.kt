/*
 * Copyright (c) 2022 Proton Technologies AG
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
package com.protonvpn.android.models.vpn

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class PartnersResponse(
    @SerialName(value = "PartnerTypes") val partnerTypes: List<PartnerType>,
)

@Serializable
data class PartnerType(
    @SerialName(value = "Type") val type: String? = null,
    @SerialName(value = "Description") val description: String? = null,
    @SerialName(value = "IconURL") val iconUrl: String? = null,
    @SerialName(value = "Partners") val partners: List<Partner> = emptyList()
)

@Parcelize
@Serializable
data class Partner(
    @SerialName(value = "Name") val name: String? = null,
    @SerialName(value = "Description") val description: String? = null,
    @SerialName(value = "IconURL") val iconUrl: String? = null,
    @SerialName(value = "LogicalIDs") val logicalIDs: List<String> = emptyList(),
) : Parcelable
