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

package com.protonvpn.android.excludedlocations.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.base.ui.SimpleModalBottomSheet
import com.protonvpn.android.base.ui.VpnSolidButton
import com.protonvpn.android.base.ui.VpnTextButton
import com.protonvpn.android.redesign.base.ui.previews.PreviewBooleanProvider
import me.proton.core.compose.theme.ProtonTheme

@Composable
fun ExcludedLocationsAdoptionBottomSheet(
    onAction: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SimpleModalBottomSheet(
        modifier = modifier,
        onDismissRequest = onDismiss,
    ) {
        ExcludedLocationsAdoptionBottomSheetContent(
            modifier = Modifier.padding(all = 16.dp),
            onAction = onAction,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun ExcludedLocationsAdoptionBottomSheetContent(
    onAction: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(space = 16.dp),
    ) {
        Text(
            text = stringResource(id = R.string.excluded_locations_adoption_title),
            style = ProtonTheme.typography.subheadline,
        )

        Image(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape = ProtonTheme.shapes.medium),
            painter = painterResource(id = R.drawable.background_excluded_locations),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
        )

        Text(
            modifier = Modifier.padding(vertical = 8.dp),
            text = AnnotatedString.fromHtml(
                htmlString = stringResource(id = R.string.excluded_locations_adoption_description),
                linkStyles = null,
                linkInteractionListener = null,
            ),
            style = ProtonTheme.typography.body2Regular,
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(space = 8.dp),
        ) {
            VpnSolidButton(
                text = stringResource(id = R.string.exclude_locations),
                onClick = onAction,
            )

            VpnTextButton(
                text = stringResource(id = R.string.no_thanks),
                onClick = onDismiss,
            )
        }
    }
}

@Preview
@Composable
private fun ExcludedLocationsAdoptionBottomSheetContentPreview(
    @PreviewParameter(PreviewBooleanProvider::class) isDark: Boolean,
) {
    ProtonVpnPreview(isDark = isDark) {
        ExcludedLocationsAdoptionBottomSheetContent(
            onAction = {},
            onDismiss = {},
        )
    }
}
