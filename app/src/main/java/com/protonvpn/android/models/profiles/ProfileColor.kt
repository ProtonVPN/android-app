/*
 * Copyright (c) 2021. Proton AG
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

package com.protonvpn.android.models.profiles

import androidx.annotation.ColorRes
import me.proton.core.presentation.R

enum class ProfileColor(
    val id: Int,
    @ColorRes val colorRes: Int
) {
    PURPLE(1, R.color.purple_base),
    STRAWBERRY(3, R.color.strawberry_base),
    PINK(5, R.color.pink_base),
    SLATEBLUE(7, R.color.slateblue_base),
    PACIFIC(9, R.color.pacific_base),
    REEF(11, R.color.reef_base),
    FERN(13, R.color.fern_base),
    OLIVE(16, R.color.olive_base),
    SAHARA(18, R.color.sahara_base),
    CARROT(19, R.color.carrot_base);

    companion object {

        val legacyColors by lazy {
            mapOf(
                "#FFE01623" to STRAWBERRY,
                "#FFBD6566" to STRAWBERRY,
                "#FFD21BB5" to PINK,
                "#FFB14FA3" to PINK,
                "#FF9F49DA" to SLATEBLUE,
                "#FF9369B1" to SLATEBLUE,
                "#FF6071DA" to PACIFIC,
                "#FF6871A5" to PACIFIC,
                "#FF39C5CA" to REEF,
                "#FF579191" to REEF,
                "#FF3DB965" to FERN,
                "#FF5C946F" to FERN,
                "#FF9DBF3A" to OLIVE,
                "#FF909F66" to OLIVE,
                "#FFE9C652" to SAHARA,
                "#FFA39364" to SAHARA,
                "#FFD77124" to CARROT,
                "#FFA97D57" to CARROT
            )
        }

        @JvmStatic
        fun byId(id: Int) = values().find { it.id == id }
        @JvmStatic
        fun random() = values().random()
    }
}