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

package com.protonvpn.android.redesign.base.ui

import androidx.compose.ui.graphics.Color
import me.proton.core.compose.theme.ProtonColors

@Suppress("MagicNumber")
val ProtonColors.vpnGreen: Color
    get() = if (isDark) Color(0xFF2CFFCC) else Color(0xFF1C9C7C)

val ProtonColors.upsellGradientStart: Color
    get() = Color(0x6611D8CC)

val ProtonColors.upsellGradientEnd: Color
    get() = Color(0x006E4BFF)

val ProtonColors.upsellBorderGradientStart: Color
    get() = Color(0xff4B29D9)

val ProtonColors.upsellBorderGradientEnd: Color
    get() = Color(0xff2CDCCB)
