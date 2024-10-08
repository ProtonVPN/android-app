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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.redesign.base.ui.FlagFastest
import com.protonvpn.android.redesign.base.ui.ConnectIntentIcon
import com.protonvpn.android.redesign.base.ui.FlagRecentConnection
import com.protonvpn.android.redesign.recents.ui.DefaultConnectionViewModel
import com.protonvpn.android.redesign.recents.usecases.DefaultConnItem
import com.protonvpn.android.redesign.vpn.ServerFeature
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentBlankRow
import com.protonvpn.android.redesign.vpn.ui.label
import me.proton.core.compose.theme.ProtonTheme


@Composable
fun DefaultConnectionSetting(onClose: () -> Unit) {
    SubSettingWithLazyContent(
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
                                leadingIcon = { ConnectIntentIcon(item.connectIntentViewState.primaryLabel) },
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
    leadingIcon: @Composable RowScope.() -> Unit,
    title: String,
    subTitle: AnnotatedString?,
    serverFeatures: Set<ServerFeature>,
    isSelected: Boolean,
    onSelected: () -> Unit
) {
    ConnectIntentBlankRow(
        leadingComposable = leadingIcon,
        trailingComposable = { RadioButton(
            selected = isSelected,
            onClick = onSelected,
            modifier = Modifier.clearAndSetSemantics { }
        ) },
        title = title,
        subTitle = subTitle,
        serverFeatures = serverFeatures,
        modifier = Modifier
            .selectable(isSelected, onClick = onSelected)
            .padding(horizontal = 16.dp)
    )
}

@Preview
@Composable
fun DefaultSelectionPreview() {
    ProtonVpnPreview {
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
