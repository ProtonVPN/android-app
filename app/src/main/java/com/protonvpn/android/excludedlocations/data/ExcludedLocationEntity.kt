/*
 * Copyright (c) 2025 Proton AG
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

package com.protonvpn.android.excludedlocations.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.excludedlocations.ExcludedLocation
import me.proton.core.account.data.entity.AccountEntity
import me.proton.core.domain.entity.UserId

@Entity(
    tableName = "excludedLocations",
    indices = [
        Index(value = ["userId", "countryCode", "nameEn"], unique = true),
    ],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["userId"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ExcludedLocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    val countryCode: String,
    val nameEn: String,
    val type: ExcludedLocationType,
)

enum class ExcludedLocationType {
    City,
    Country,
    State,
}

fun List<ExcludedLocationEntity>.toDomain(): List<ExcludedLocation> = map(ExcludedLocationEntity::toDomain)

fun ExcludedLocationEntity.toDomain(): ExcludedLocation = when (type) {
    ExcludedLocationType.City -> {
        ExcludedLocation.City(
            id = id,
            countryId = CountryId(countryCode = countryCode),
            nameEn = nameEn,
        )
    }

    ExcludedLocationType.Country -> {
        ExcludedLocation.Country(
            id = id,
            countryId = CountryId(countryCode = countryCode),
        )
    }

    ExcludedLocationType.State -> {
        ExcludedLocation.State(
            id = id,
            countryId = CountryId(countryCode = countryCode),
            nameEn = nameEn,
        )
    }
}

fun ExcludedLocation.toEntity(userId: UserId): ExcludedLocationEntity = when (this) {
    is ExcludedLocation.City -> ExcludedLocationType.City to nameEn
    is ExcludedLocation.Country -> ExcludedLocationType.Country to null
    is ExcludedLocation.State -> ExcludedLocationType.State to nameEn
}.let { (type, nameEn) ->
    ExcludedLocationEntity(
        id = id,
        userId = userId.id,
        countryCode = countryId.countryCode,
        // This property usually it would be nullable because we only store the name in English for Cities
        // and States but never for Countries. But, since nameEn is needed to generate unique indices,
        // we need to fallback to an empty string otherwise Room ignores it and we can end up with duplicated entries.
        nameEn = nameEn.orEmpty(),
        type = type,
    )
}
