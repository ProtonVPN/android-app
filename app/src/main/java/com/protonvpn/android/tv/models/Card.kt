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

import androidx.annotation.DrawableRes
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.vpn.VpnCountry
import java.io.Serializable

sealed class Card(
    var title: String,
    @DrawableRes val image: Int
) : Serializable

class CountryCard(title: String, @DrawableRes image: Int, val vpnCountry: VpnCountry) : Card(title, image)
class ProfileCard(title: String, @DrawableRes image: Int, val profile: Profile) : Card(title, image)
class IconCard(title: String, @DrawableRes image: Int) : Card(title, image)
class DetailedIconCard(
    title: String,
    @DrawableRes image: Int,
    val backgroundImage: BackgroundImage,
    val description: String,
    val subDescription: String
) : Card(title, image)

class BackgroundImage(@DrawableRes val resId: Int, val opacity: Float)
