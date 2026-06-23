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

package com.protonvpn.android.ui.settings

import android.graphics.drawable.BitmapDrawable
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import me.proton.core.presentation.R as CoreR

@Composable
fun LabeledItemRow(
    item: LabeledItem,
    onAction: () -> Unit,
    @StringRes actionLabelRes: Int,
    @DrawableRes actionIconRes: Int,
    modifier: Modifier = Modifier.Companion,
) {
    val actionLabel = stringResource(actionLabelRes)
    Row(
        modifier = modifier
            .semantics(mergeDescendants = true) {
                onClick(label = actionLabel) {
                    onAction()
                    true
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.iconDrawable != null || item.iconRes != 0) {
            val painter = when {
                // Only bitmaps are currently supported. This is enough for current use because
                // InstalledAppsProvider converts all icons to small bitmaps.
                item.iconDrawable is BitmapDrawable -> BitmapPainter(item.iconDrawable.bitmap.asImageBitmap())
                item.iconRes > 0 -> painterResource(item.iconRes)
                else -> {
                    ProtonLogger.logCustom(
                        LogCategory.UI,
                        "Icon drawable type: ${item.iconDrawable?.javaClass} is not handled. ${item.label}"
                    )
                    painterResource(CoreR.drawable.ic_proton_brand_android)
                }
            }
            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = item.label,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 14.dp)
        )
        Spacer(Modifier.Companion.width(4.dp))
        IconButton(
            onClick = onAction,
        ) {
            Icon(
                painterResource(actionIconRes),
                contentDescription = null
            )
        }
    }
}

@Composable
fun LabeledItemRowWithRemove(
    item: LabeledItem,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LabeledItemRow(
        item = item,
        onAction = onRemove,
        actionLabelRes = R.string.remove,
        actionIconRes = CoreR.drawable.ic_proton_minus_circle_filled,
        modifier = modifier,
    )
}

@Composable
fun LabeledItemRowWithAdd(
    item: LabeledItem,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LabeledItemRow(
        item = item,
        onAction = onAdd,
        actionLabelRes = R.string.add,
        actionIconRes = CoreR.drawable.ic_proton_plus_circle,
        modifier = modifier,
    )
}
