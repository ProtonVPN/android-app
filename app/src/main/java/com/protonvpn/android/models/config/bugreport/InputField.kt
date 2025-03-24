/*
 * Copyright (c) 2021 Proton AG
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
package com.protonvpn.android.models.config.bugreport

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val TYPE_SINGLELINE = "TextSingleLine"
const val TYPE_MULTILINE = "TextMultiLine"
const val TYPE_DROPDOWN = "Dropdown"

@Serializable
data class InputField(
    @SerialName("Label") val label: String,
    @SerialName("Placeholder") val placeholder: String? = null,
    @SerialName("IsMandatory") val isMandatory: Boolean = true,
    @SerialName("SubmitLabel") val submitLabel: String,
    @SerialName("Options") val dropdownOptions: List<DropdownField> = emptyList(),
    @SerialName("Type") val type: String
) : java.io.Serializable

@Serializable
data class DropdownField(
    @SerialName("Label") val label: String,
    @SerialName("SubmitLabel") val submitLabel: String,
) : java.io.Serializable