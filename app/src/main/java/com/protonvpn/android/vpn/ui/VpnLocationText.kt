/*
 * Copyright (c) 2026. Proton AG
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

package com.protonvpn.android.vpn.ui

import android.text.BidiFormatter
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.protonvpn.android.R
import me.proton.core.compose.theme.ProtonTheme

/**
 * "Country · IP" with the country highlighted. Only the country is shown when ip is null.
 */
@Composable
fun vpnLocationText(country: String, ip: String?): AnnotatedString {
    val wrappedCountry = BidiFormatter.getInstance().unicodeWrap(country)
    val text = if (ip != null) {
        stringResource(R.string.vpn_status_disabled_location, wrappedCountry, ip)
    } else {
        wrappedCountry
    }
    val highlightColor = ProtonTheme.colors.textNorm
    return buildAnnotatedString {
        append(text)
        val startIndex = text.indexOf(wrappedCountry)
        if (startIndex >= 0) {
            addStyle(SpanStyle(color = highlightColor), startIndex, startIndex + wrappedCountry.length)
        }
    }
}
