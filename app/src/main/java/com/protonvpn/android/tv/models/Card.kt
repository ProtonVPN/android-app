/*
 * Copyright (c) 2020 Proton AG
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
import com.protonvpn.android.models.vpn.VpnCountry
import com.protonvpn.android.redesign.vpn.ConnectIntent
import me.proton.core.presentation.R as CoreR

sealed class Card(
    var title: Title? = null,
    var bottomTitle: Title? = null,
    val backgroundImage: DrawableImage
)

class CountryCard(
    val countryName: String,
    backgroundImage: DrawableImage,
    @DrawableRes bottomTitleResId: Int?,
    val vpnCountry: VpnCountry
) : Card(
    bottomTitle = Title(countryName, bottomTitleResId),
    backgroundImage = backgroundImage
)

class ConnectIntentCard(
    title: String = "",
    @DrawableRes titleDrawable: Int = CoreR.drawable.ic_proton_bolt,
    @DrawableRes backgroundImage: Int,
    val connectIntent: ConnectIntent,
    val connectCountry: String
) : Card(
    bottomTitle = Title(title, titleDrawable),
    backgroundImage = DrawableImage(backgroundImage)
)

class QuickConnectCard(title: Title, backgroundImage: DrawableImage) : Card(
    bottomTitle = title,
    backgroundImage = backgroundImage
)

open class IconCard(title: String, @DrawableRes image: Int) : Card(
    title = Title(title), backgroundImage = DrawableImage(image)
)

class LogoutCard(title: String) : IconCard(title, CoreR.drawable.ic_proton_arrow_out_from_rectangle)
class ReportBugCard(title: String) : IconCard(title, CoreR.drawable.ic_proton_bug)
class SettingsAutoConnectCard(title: String) :
    IconCard(title, CoreR.drawable.ic_proton_power_off)

class SettingsCustomDns(title: String, isFree: Boolean) : IconCard(
    title = title,
    image = iconPaidFeature(isFree = isFree, iconRes = CoreR.drawable.ic_proton_servers)
)

class SettingsIPv6ConnectionsCard(title: String) :
    IconCard(title, CoreR.drawable.ic_proton_globe)

class SettingsLanConnectionsCard(title: String, isFree: Boolean) :
    IconCard(title, iconPaidFeature(isFree, CoreR.drawable.ic_proton_arrow_right_arrow_left))

class SettingsNetShieldCard(title: String, isFree: Boolean) :
    IconCard(title, iconPaidFeature(isFree, CoreR.drawable.ic_proton_shield_filled))

class SettingsProtocolCard(title: String) : IconCard(title, CoreR.drawable.ic_proton_shield_2_bolt)
class SettingsSplitTunnelingCard(title: String, isFree: Boolean) :
    IconCard(title, iconPaidFeature(isFree, CoreR.drawable.ic_proton_arrows_swap_right))

class Title(
    val text: String,
    @DrawableRes val resId: Int? = null,
    @ColorRes var backgroundColorRes: Int = R.color.tvGridItemOverlay
)

class DrawableImage(
    @DrawableRes val resId: Int,
    @ColorRes val tintRes: Int? = null
)

private fun iconPaidFeature(isFree: Boolean, @DrawableRes iconRes: Int) =
    if (isFree) R.drawable.vpn_plus_badge else iconRes
