/*
 * Copyright (c) 2020 Proton Technologies AG
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
package com.protonvpn.android.tv.models

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.protonvpn.android.R
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.vpn.VpnCountry
import java.io.Serializable

sealed class Card(
    var title: Title? = null,
    var bottomTitle: Title? = null,
    val backgroundImage: DrawableImage?
) : Serializable

class CountryCard(
    val countryName: String,
    hasStreamingService: Boolean = false,
    backgroundImage: DrawableImage,
    @DrawableRes bottomTitleResId: Int?,
    val vpnCountry: VpnCountry
) : Card(
    title = if (hasStreamingService) Title("", R.drawable.ic_play) else null,
    bottomTitle = Title(countryName, bottomTitleResId),
    backgroundImage = backgroundImage
)

class ProfileCard(title: String? = null, @DrawableRes backgroundImage: Int, val profile: Profile) : Card(
    bottomTitle = Title("", R.drawable.ic_thunder),
    backgroundImage = DrawableImage(backgroundImage)
)

class QuickConnectCard(title: Title, backgroundImage: DrawableImage) : Card(
    bottomTitle = title,
    backgroundImage = backgroundImage
)

open class IconCard(title: String, @DrawableRes image: Int) : Card(
    title = Title(title), backgroundImage = DrawableImage(image)
)

class LogoutCard(title: String) : IconCard(title, R.drawable.ic_tv_icon_logout)

class Title(
    val text: String,
    @DrawableRes val resId: Int? = null,
    @ColorRes var backgroundColorRes: Int = R.color.tvGridItemOverlay
) : Serializable

class DrawableImage(
    @DrawableRes val resId: Int,
    @ColorRes val tint: Int? = null
) : Serializable
