/*
 * Copyright (c) 2026 Proton AG
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

package com.protonvpn.android.base.ui.cards

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.ProtonVpnPreview
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.presentation.R as CoreR

@Composable
fun InfoCard(
    title: String,
    details: String,
    @DrawableRes iconId: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = ProtonTheme.colors.backgroundNorm,
    additionalInformation: (@Composable () -> Unit)? = null,
) {
    ConstraintLayout(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape = ProtonTheme.shapes.medium)
            .clickable(onClick = onClick)
            .background(color = backgroundColor)
            .padding(16.dp)
    ) {
        val (icon, titleView, detailsView, additionalView) = createRefs()

        Icon(
            painter = painterResource(id = iconId),
            tint = ProtonTheme.colors.iconNorm,
            contentDescription = null,
            modifier = Modifier
                .size(20.dp)
                .constrainAs(icon) {
                    start.linkTo(parent.start)
                    top.linkTo(parent.top)
                }
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.constrainAs(titleView) {
                start.linkTo(icon.end, margin = 8.dp)
                end.linkTo(parent.end)
                top.linkTo(icon.top)
                width = Dimension.fillToConstraints
            }
        ) {
            Text(
                text = title,
                style = ProtonTheme.typography.body2Medium,
                color = ProtonTheme.colors.textNorm,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = stringResource(id = R.string.connection_details_info_label),
                style = ProtonTheme.typography.body2Medium,
                color = ProtonTheme.colors.textWeak,
                modifier = Modifier.padding(end = 4.dp)
            )

            Icon(
                painter = painterResource(id = CoreR.drawable.ic_info_circle),
                tint = ProtonTheme.colors.iconHint,
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .padding(end = 4.dp)
            )
        }

        Text(
            text = details,
            style = ProtonTheme.typography.body2Regular,
            color = ProtonTheme.colors.textWeak,
            modifier = Modifier.constrainAs(detailsView) {
                start.linkTo(titleView.start)
                end.linkTo(titleView.end)
                top.linkTo(titleView.bottom, margin = 8.dp)
                width = Dimension.fillToConstraints
            }
        )

        additionalInformation?.let {
            Box(
                modifier = Modifier.constrainAs(additionalView) {
                    start.linkTo(parent.start)
                    top.linkTo(detailsView.bottom, margin = 8.dp)
                    end.linkTo(parent.end)
                    width = Dimension.fillToConstraints
                }
            ) {
                it()
            }
        }
    }
}

@ProtonVpnPreview
@Composable
private fun InfoCardPreview() {
    ProtonVpnPreview {
        Column {
            InfoCard(
                title = "P2P",
                details = "Very long text to indicate multiple lines. Very long text to indicate multiple lines. Very long text to indicate multiple lines.",
                iconId = CoreR.drawable.ic_proton_brand_tor,
                onClick = {}
            )

            InfoCard(
                title = stringResource(id = R.string.connection_feature_streaming_title),
                details = stringResource(id = R.string.connection_feature_streaming_description),
                iconId = CoreR.drawable.ic_proton_play,
                additionalInformation = {
                    Text(
                        text = "Additional composable here",
                        style = ProtonTheme.typography.body2Medium,
                        color = ProtonTheme.colors.textNorm,
                    )
                },
                onClick = {},
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}
