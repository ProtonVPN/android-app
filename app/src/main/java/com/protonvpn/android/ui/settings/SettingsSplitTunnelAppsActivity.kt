/*
 * Copyright (c) 2021. Proton AG
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

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.ProtonTextButton
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.base.ui.SimpleTopAppBar
import com.protonvpn.android.base.ui.TextSectionHeader
import com.protonvpn.android.base.ui.TopAppBarBackIcon
import com.protonvpn.android.base.ui.largeScreenContentPadding
import com.protonvpn.android.base.ui.theme.VpnTheme
import com.protonvpn.android.base.ui.theme.enableEdgeToEdgeVpn
import com.protonvpn.android.settings.data.SplitTunnelingMode
import com.protonvpn.android.ui.SaveableSettingsActivity
import com.protonvpn.android.utils.getSerializableExtraCompat
import dagger.hilt.android.AndroidEntryPoint
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.presentation.R as CoreR

@AndroidEntryPoint
class SettingsSplitTunnelAppsActivity : SaveableSettingsActivity<SettingsSplitTunnelAppsViewModel>() {

    override val viewModel: SettingsSplitTunnelAppsViewModel by viewModels()

    private lateinit var mode: SplitTunnelingMode
    private var showDiscardChangesDialog by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdgeVpn()
        super.onCreate(savedInstanceState)

        mode = requireNotNull(intent.getSerializableExtraCompat<SplitTunnelingMode>(SPLIT_TUNNELING_MODE_KEY))
        setContent {
            VpnTheme {
                val state by viewModel.viewState.collectAsStateWithLifecycle()
                SplitTunnelingApps(
                    mode = mode,
                    viewState = state,
                    onAdd = viewModel::addApp,
                    onRemove = viewModel::removeApp,
                    onToggleSystemApps = viewModel::toggleLoadSystemApps,
                    onBack = viewModel::onGoBack,
                    onSave = viewModel::saveAndClose,
                    modifier = Modifier.fillMaxSize()
                )

                if (showDiscardChangesDialog) {
                    DiscardChangesDialog(
                        onDismissRequest = { showDiscardChangesDialog = false }
                    )
                }
            }
        }
    }

    override fun showDiscardChangesDialog() {
        showDiscardChangesDialog = true
    }

    companion object {
        const val SPLIT_TUNNELING_MODE_KEY = "split tunneling mode"

        fun createContract() = createContract<SplitTunnelingMode>(SettingsSplitTunnelAppsActivity::class) { mode ->
            putExtra(SPLIT_TUNNELING_MODE_KEY, mode)
        }
    }
}

@VisibleForTesting
@Composable
fun SplitTunnelingApps(
    mode: SplitTunnelingMode,
    viewState: SplitTunnelingAppsViewModelHelper.ViewState,
    onAdd: (LabeledItem) -> Unit,
    onRemove: (LabeledItem) -> Unit,
    onToggleSystemApps: () -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val titleRes = when (mode) {
        SplitTunnelingMode.INCLUDE_ONLY -> R.string.settings_split_tunneling_included_apps
        SplitTunnelingMode.EXCLUDE_ONLY -> R.string.settings_split_tunneling_excluded_apps
    }
    val listState = rememberLazyListState()
    Scaffold(
        topBar = {
            SimpleTopAppBar(
                title = { Text(stringResource(titleRes)) },
                navigationIcon = { TopAppBarBackIcon(onBack) },
                isScrolledPredicate = { listState.canScrollBackward },
            ) {
                ProtonTextButton(onSave) {
                    Text(
                        stringResource(R.string.saveButton),
                        style = ProtonTheme.typography.body1Medium,
                    )
                }
            }
        },
        modifier = modifier,
    ) { paddingValues ->
        when (viewState) {
            SplitTunnelingAppsViewModelHelper.ViewState.Loading -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                ) {
                    CircularProgressIndicator()
                }
            }
            is SplitTunnelingAppsViewModelHelper.ViewState.Content -> {
                SplitTunnelAppsLazyList(
                    mode = mode,
                    content = viewState,
                    listState = listState,
                    onAdd = onAdd,
                    onRemove = onRemove,
                    onToggleSystemApps = onToggleSystemApps,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                )
            }
        }
    }
}

@Composable
private fun SplitTunnelAppsLazyList(
    mode: SplitTunnelingMode,
    content: SplitTunnelingAppsViewModelHelper.ViewState.Content,
    listState: LazyListState,
    onAdd: (LabeledItem) -> Unit,
    onRemove: (LabeledItem) -> Unit,
    onToggleSystemApps: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val labeledItemModifier = Modifier
        .fillMaxWidth()
        .largeScreenContentPadding()
        .padding(start = 16.dp, end = 8.dp)
    LazyColumn(
        state = listState,
        modifier = modifier,
    ) {
        item(key = "header_selected") {
            val headerRes =
                if (mode == SplitTunnelingMode.INCLUDE_ONLY) R.string.settingsIncludedAppsSelectedHeader
                else R.string.settingsExcludedAppsSelectedHeader
            val itemModifier = Modifier
                .padding(horizontal = 16.dp)
                .largeScreenContentPadding()
                .animateItem()
            TextSectionHeader(
                text = stringResource(headerRes, content.selectedApps.size),
                bottomPadding = 4.dp,
                modifier = itemModifier,
            )
            val explanationText = when(mode) {
                SplitTunnelingMode.INCLUDE_ONLY -> R.string.settingsSplitTunnelingIncludedAppsList
                SplitTunnelingMode.EXCLUDE_ONLY -> R.string.settingsSplitTunnelingExcludedAppsList
            }
            Text(
                text = stringResource(explanationText),
                style = ProtonTheme.typography.body2Medium,
                color = ProtonTheme.colors.textWeak,
                modifier = itemModifier,
            )
        }

        items(
            items = content.selectedApps,
            key = { it.id }
        ) { item ->
            LabeledItemRowWithRemove(
                item = item,
                onRemove = { onRemove(item) },
                modifier = labeledItemModifier.animateItem()
            )
        }

        item(key = "header_regular_apps") {
            TextSectionHeader(
                text = stringResource(
                    R.string.settingsSplitTunnelingAvailableRegularHeader,
                    content.availableRegularApps.size
                ),
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .largeScreenContentPadding()
                    .animateItem()
            )
        }

        items(
            items = content.availableRegularApps,
            key = { it.id }
        ) { item ->
            LabeledItemRowWithAdd(
                item = item,
                onAdd = { onAdd(item) },
                modifier = labeledItemModifier.animateItem()
            )
        }

        item(key = "system_apps_checkbox") {
            val isChecked =
                content.availableSystemApps !is SplitTunnelingAppsViewModelHelper.SystemAppsState.NotLoaded
            SystemAppsCheckboxRow(
                isChecked = isChecked,
                onValueChange = { onToggleSystemApps() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .largeScreenContentPadding()
                    .animateItem()
            )
        }

        when (content.availableSystemApps) {
            is SplitTunnelingAppsViewModelHelper.SystemAppsState.NotLoaded -> {
                item(key = "system_apps_loading") {
                    // Same height as the progress indicator for loading.
                    Spacer(Modifier.height(64.dp))
                }
            }

            is SplitTunnelingAppsViewModelHelper.SystemAppsState.Loading -> {
                item(key = "system_apps_loading") {
                    Box(
                        Modifier.heightIn(min = 64.dp)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.animateItem())
                    }
                }
            }
            is SplitTunnelingAppsViewModelHelper.SystemAppsState.Content -> {
                items(
                    items = content.availableSystemApps.apps,
                    key = { it.id }
                ) { item ->
                    LabeledItemRowWithAdd(
                        item = item,
                        onAdd = { onAdd(item) },
                        modifier = labeledItemModifier.animateItem()
                    )
                }
            }
        }
    }
}

@Composable
private fun SystemAppsCheckboxRow(
    isChecked: Boolean,
    onValueChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .toggleable(
                value = isChecked,
                role = Role.Checkbox,
                onValueChange = onValueChange
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.settingsExcludedAppsLoadSystemAppsButton),
            modifier = Modifier
                .padding(vertical = 14.dp)
                .weight(1f)
        )
        Checkbox(
            checked = isChecked,
            onCheckedChange = null,
            modifier = Modifier
                .padding(4.dp)
                .clearAndSetSemantics() {}
        )
    }
}

@ProtonVpnPreview
@Composable
private fun SplitTunnelingAppsPreview() {
    ProtonVpnPreview {
        val viewState = SplitTunnelingAppsViewModelHelper.ViewState.Content(
            selectedApps = listOf(
                LabeledItem("1", "App 1", CoreR.drawable.ic_proton_brand_proton_vpn)
            ),
            availableRegularApps = listOf(
                LabeledItem("2", "Calendar", CoreR.drawable.ic_proton_brand_proton_calendar),
                LabeledItem("3", "Mail", CoreR.drawable.ic_proton_brand_proton_mail),
                LabeledItem("4", "Pass", CoreR.drawable.ic_proton_brand_proton_pass),
                LabeledItem("5", "Drive", CoreR.drawable.ic_proton_brand_proton_drive),
            ),
            availableSystemApps = SplitTunnelingAppsViewModelHelper.SystemAppsState.NotLoaded(emptyList())
        )
        SplitTunnelingApps(
            mode = SplitTunnelingMode.EXCLUDE_ONLY,
            viewState = viewState,
            {}, {}, {}, {}, {}
        )
    }
}
