/*
 * Copyright (c) 2023. Proton AG
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

package com.protonvpn.android.redesign.recents.data

import androidx.room.TypeConverter
import com.protonvpn.android.redesign.vpn.ServerFeature
import java.util.EnumSet

class RecentsTypeConverters {
    @TypeConverter
    fun fromFeatureSetToString(value: Set<ServerFeature>): String =
        value.joinToString(",")

    @TypeConverter
    fun fromStringToFeatureSet(string: String): Set<ServerFeature> =
        string.split(",").mapNotNullTo(EnumSet.noneOf(ServerFeature::class.java)) { featureName ->
            ServerFeature.values().firstOrNull { it.name == featureName }
        }

    @TypeConverter
    fun fromConnectIntentTypeToString(value: ConnectIntentType): String =
        value.toString()

    @TypeConverter
    fun fromStringToConnectIntentType(string: String): ConnectIntentType =
        ConnectIntentType.values().first { it.name == string }
}
