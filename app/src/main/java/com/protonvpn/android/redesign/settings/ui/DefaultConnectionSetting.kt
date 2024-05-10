/*
 * Copyright (c) 2024. Proton AG
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
package com.protonvpn.android.redesign.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.theme.LightAndDarkPreview
import com.protonvpn.android.redesign.base.ui.FlagFastest
import com.protonvpn.android.redesign.base.ui.FlagOrGatewayIndicator
import com.protonvpn.android.redesign.base.ui.FlagRecentConnection
import com.protonvpn.android.redesign.recents.ui.DefaultConnectionViewModel
import com.protonvpn.android.redesign.recents.usecases.DefaultConnItem
import com.protonvpn.android.redesign.vpn.ServerFeature
import com.protonvpn.android.redesign.vpn.ui.ServerDetailsRow
import com.protonvpn.android.redesign.vpn.ui.label
import me.proton.core.compose.theme.ProtonTheme


@Composable
fun DefaultConnectionSetting(onClose: () -> Unit) {
    SubSetting(
        title = stringResource(id = R.string.settings_default_connection_title),
        onClose = onClose
    ) {
        DefaultConnectionSelection(onClose = onClose)
    }
}

@Composable
fun DefaultConnectionSelection(
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
) {
    val viewModel: DefaultConnectionViewModel = hiltViewModel()
    val defaultConnectionViewState =
        viewModel.defaultConnectionViewState.collectAsStateWithLifecycle(null)
    val onSelected = { defaultConnection: DefaultConnItem ->
        viewModel.setNewDefaultConnection(defaultConnection)
        onClose()
    }
    defaultConnectionViewState.value?.let {
        LazyColumn(
            modifier = modifier.padding(vertical = 16.dp)
        ) {
            it.recents.forEach { item ->
                item {
                    when (item) {
                        is DefaultConnItem.DefaultConnItemViewState -> {
                            DefaultSelectionRow(
                                leadingIcon = { FlagOrGatewayIndicator(item.connectIntentViewState.primaryLabel) },
                                title = item.connectIntentViewState.primaryLabel.label(),
                                subTitle = item.connectIntentViewState.secondaryLabel?.label(),
                                serverFeatures = item.connectIntent.features,
                                isSelected = item.isDefaultConnection,
                                onSelected = { onSelected(item) }
                            )
                        }

                        is DefaultConnItem.PreDefinedItem -> {
                            DefaultSelectionRow(
                                leadingIcon = {
                                    if (item is DefaultConnItem.MostRecentItem) FlagRecentConnection() else FlagFastest(
                                        isSecureCore = false,
                                        connectedCountry = null
                                    )
                                },
                                title = stringResource(id = item.titleRes),
                                subTitle = buildAnnotatedString {
                                    append(stringResource(id = item.subtitleRes))
                                },
                                isSelected = item.isDefaultConnection,
                                serverFeatures = emptySet(),
                                onSelected = { onSelected(item) }
                            )
                        }

                        is DefaultConnItem.HeaderSeparator -> {
                            Text(
                                text = stringResource(item.titleRes),
                                style = ProtonTheme.typography.body2Regular,
                                color = ProtonTheme.colors.textWeak,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DefaultSelectionRow(
    leadingIcon: @Composable () -> Unit,
    title: String,
    subTitle: AnnotatedString?,
    serverFeatures: Set<ServerFeature>,
    isSelected: Boolean,
    onSelected: () -> Unit
) {
    Row(
        modifier = Modifier
            .heightIn(min = 42.dp)
            .selectable(isSelected, onClick = onSelected)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = if (subTitle != null || serverFeatures.isNotEmpty()) Alignment.Top else Alignment.CenterVertically,
    ) {
        leadingIcon()
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp)
        ) {
            Text(
                text = title,
                style = ProtonTheme.typography.body1Regular,
            )
            if (subTitle != null || serverFeatures.isNotEmpty()) {
                ServerDetailsRow(
                    subTitle,
                    null,
                    serverFeatures,
                    detailsStyle = ProtonTheme.typography.body2Regular,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        RadioButton(
            selected = isSelected,
            onClick = onSelected,
            modifier = Modifier.clearAndSetSemantics { }
        )
    }
}

@Preview
@Composable
fun DefaultSelectionPreview() {
    LightAndDarkPreview {
        Column {
            DefaultSelectionRow(
                leadingIcon = { FlagRecentConnection() },
                title = "Most recent",
                subTitle = buildAnnotatedString { append("#53-TOR") },
                serverFeatures = setOf(ServerFeature.Tor),
                isSelected = true,
                onSelected = {}
            )
        }
    }
}
