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

package com.protonvpn.android.models.profiles

import androidx.annotation.ColorRes
import com.protonvpn.android.R

enum class ProfileColor(
    val id: Int,
    @ColorRes val colorRes: Int,
    val legacyColorString: String?
) {
    PURPLE(1, R.color.purple_base, null),
    HEATHER(2, R.color.heather_base, null),
    STRAWBERRY(3, R.color.strawberry_base, "#FFE01623"),
    BLUSH(4, R.color.blush_base, "#FFBD6566"),
    PINK(5, R.color.pink_base, "#FFD21BB5"),
    LILAC(6, R.color.lilac_base, "#FFB14FA3"),
    SLATEBLUE(7, R.color.slateblue_base, "#FF9F49DA"),
    KIMBERLY(8, R.color.kimberly_base, "#FF9369B1"),
    PACIFIC(9, R.color.pacific_base, "#FF6071DA"),
    GLACIER(10, R.color.glacier_base, "#FF6871A5"),
    PROFILE_11(11, R.color.profile11, "#FF39C5CA"),
    PROFILE_12(12, R.color.profile12, "#FF579191"),
    FERN(13, R.color.fern_base, "#FF3DB965"),
    MOSS(14, R.color.moss_base, "#FF5C946F"),
    PEAR(15, R.color.pear_base, "#FF9DBF3A"),
    OLIVE(16, R.color.olive_base, "#FF909F66"),
    MUSTARD(17, R.color.mustard_base, "#FFE9C652"),
    SAHARA(18, R.color.sahara_base, "#FFA39364"),
    CARROT(19, R.color.carrot_base, "#FFD77124"),
    CINNAMON(20, R.color.cinnamon_base, "#FFA97D57");

    companion object {
        @JvmStatic
        fun byId(id: Int) = requireNotNull(values().find { it.id == id })
        @JvmStatic
        fun random() = values().random()
    }
}
