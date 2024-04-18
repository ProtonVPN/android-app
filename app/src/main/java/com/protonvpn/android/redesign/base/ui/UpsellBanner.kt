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

package com.protonvpn.android.redesign.base.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.theme.LightAndDarkPreview
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.presentation.R as CoreR

// This is not a self-contained banner, caller need to deliver background, padding, click and shape
// via modifier or parent composable.
@Composable
fun UpsellBannerContent(
    @StringRes titleRes: Int?,
    @DrawableRes iconRes: Int,
    modifier: Modifier = Modifier,
    @StringRes descriptionRes: Int? = null,
    description: String? = null, // Note: takes precedence over descriptionRes
    @DrawableRes badgeIconRes: Int = ResourcesCompat.ID_NULL,
) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            modifier = Modifier.size(48.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            if (titleRes != null) {
                Text(
                    text = stringResource(titleRes),
                    style = ProtonTheme.typography.body2Medium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            val descriptionText = description ?: descriptionRes?.let { stringResource(it) }
            if (descriptionText != null) {
                Text(
                    text = descriptionText,
                    style = ProtonTheme.typography.body2Regular,
                )
            }
        }

        if (badgeIconRes != ResourcesCompat.ID_NULL) {
            Image(
                painter = painterResource(id = badgeIconRes),
                contentDescription = null
            )
        }
        Icon(
            painter = painterResource(id = CoreR.drawable.ic_proton_chevron_right),
            tint = ProtonTheme.colors.iconHint,
            contentDescription = null,
            modifier = Modifier
                .padding(start = 12.dp)
                .wrapContentSize()
        )
    }
}

@Composable
fun UpsellBanner(
    @StringRes titleRes: Int?,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @StringRes descriptionRes: Int? = null,
    description: String? = null,
    @DrawableRes badgeIconRes: Int = ResourcesCompat.ID_NULL
) {
    UpsellBannerContent(
        titleRes,
        iconRes,
        modifier = modifier
            .clip(ProtonTheme.shapes.large)
            .background(ProtonTheme.colors.backgroundSecondary)
            .clickable(onClick = onClick)
            .padding(16.dp),
        badgeIconRes = badgeIconRes,
        descriptionRes = descriptionRes,
        description = description
    )
}

@Preview
@Composable
fun UpsellBannerPreview() {
    LightAndDarkPreview {
        Surface {
            UpsellBanner(
                R.string.netshield_free_title,
                descriptionRes = R.string.netshield_free_description,
                iconRes = R.drawable.ic_netshield_promo,
                onClick = {}
            )
        }
    }
}

@Preview
@Composable
fun UpsellBannerPreviewNoTitle() {
    LightAndDarkPreview {
        Surface {
            UpsellBanner(
                null,
                descriptionRes = R.string.netshield_free_description,
                iconRes = R.drawable.ic_netshield_promo,
                onClick = {}
            )
        }
    }
}
