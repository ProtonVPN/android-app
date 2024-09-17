/*
 * Copyright (c) 2024 Proton AG
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

package com.protonvpn.android.profiles.ui

import androidx.compose.ui.graphics.Color
import com.protonvpn.android.R
import com.protonvpn.android.profiles.data.ProfileColor
import com.protonvpn.android.profiles.data.ProfileIcon

fun ProfileIcon.toDrawableRes() = when (this) {
    ProfileIcon.Icon1 -> R.drawable.profile_bolt_icon
    ProfileIcon.Icon2 -> R.drawable.profile_streaming_icon
    ProfileIcon.Icon3 -> R.drawable.profile_shield_icon
    ProfileIcon.Icon4 -> R.drawable.profile_eye_icon
    ProfileIcon.Icon5 -> R.drawable.profile_anonymous_icon
    ProfileIcon.Icon6 -> R.drawable.profile_terminal_icon
    ProfileIcon.Icon7 -> R.drawable.profile_gaming_icon
    ProfileIcon.Icon8 -> R.drawable.profile_download_icon
    ProfileIcon.Icon9 -> R.drawable.profile_business_icon
    ProfileIcon.Icon10 -> R.drawable.profile_shopping_icon
    ProfileIcon.Icon11 -> R.drawable.profile_security_icon
    ProfileIcon.Icon12 -> R.drawable.profile_browsing_icon
}

fun ProfileColor.toColor() = when (this) {
    ProfileColor.Color1 -> Color(0xFFA257FF)
    ProfileColor.Color2 -> Color(0xFF7B7BFB)
    ProfileColor.Color3 -> Color(0xFF7FDF66)
    ProfileColor.Color4 -> Color(0xFFF45E5E)
    ProfileColor.Color5 -> Color(0xFFF79F4D)
    ProfileColor.Color6 -> Color(0xFFF9E646)
}
