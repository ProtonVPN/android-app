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

package outputreport

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitlabQualityReport(
    @SerialName("description")
    val description: String,
    @SerialName("fingerprint")
    val fingerprint: String,
    @SerialName("location")
    val location: Location,
    @SerialName("severity")
    val severity: String
) {
    @Serializable
    data class Location(
        @SerialName("lines")
        val lines: Lines,
        @SerialName("path")
        val path: String
    ) {
        @Serializable
        data class Lines(
            @SerialName("begin")
            val begin: Int,
            @SerialName("end")
            val end: Int
        )
    }
}
