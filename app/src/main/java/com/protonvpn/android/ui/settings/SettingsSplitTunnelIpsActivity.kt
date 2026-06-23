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
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.ProtonTextButton
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.base.ui.SimpleTopAppBar
import com.protonvpn.android.base.ui.TextSectionHeader
import com.protonvpn.android.base.ui.TopAppBarBackIcon
import com.protonvpn.android.base.ui.largeScreenContentPadding
import com.protonvpn.android.base.ui.theme.VpnTheme
import com.protonvpn.android.base.ui.theme.enableEdgeToEdgeVpn
import com.protonvpn.android.redesign.base.ui.ProtonOutlinedTextField
import com.protonvpn.android.settings.data.SplitTunnelingMode
import com.protonvpn.android.ui.SaveableSettingsActivity
import com.protonvpn.android.utils.getSerializableExtraCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.defaultNorm
import me.proton.core.presentation.R as CoreR

@AndroidEntryPoint
class SettingsSplitTunnelIpsActivity : SaveableSettingsActivity<SettingsSplitTunnelIpsViewModel>() {

    override val viewModel: SettingsSplitTunnelIpsViewModel by viewModels()
    private lateinit var mode: SplitTunnelingMode

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdgeVpn()
        super.onCreate(savedInstanceState)

        mode = requireNotNull(intent.getSerializableExtraCompat<SplitTunnelingMode>(SPLIT_TUNNELING_MODE_KEY))
        setContent {
            VpnTheme {
                val state by viewModel.state.collectAsStateWithLifecycle(null)
                val ipInputState = rememberIpInputState(viewModel::isValidIp)
                SplitTunnelingIps(
                    mode = mode,
                    ipInputState = ipInputState,
                    ipAddresses = state?.ips,
                    onAdd = { ip ->
                        val errorMessageRes = viewModel.addAddressIfValid(ip)
                        if (errorMessageRes == null) {
                            ipInputState.clear()
                        } else {
                            ipInputState.setError(getString(errorMessageRes))
                        }
                    },
                    onRemove = { item -> confirmRemove(item) },
                    onBack = viewModel::onGoBack,
                    onSave = viewModel::saveAndClose,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        viewModel.events
            .flowWithLifecycle(lifecycle)
            .onEach { event ->
                when (event) {
                    SettingsSplitTunnelIpsViewModel.Event.ShowIPv6EnableSettingDialog ->
                        showIPv6EnableSettingDialog()

                    SettingsSplitTunnelIpsViewModel.Event.ShowIPv6EnabledToast -> {
                        Toast.makeText(
                            this@SettingsSplitTunnelIpsActivity,
                            R.string.settings_ipv6_enabled_toast,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .launchIn(lifecycleScope)
    }

    private fun showIPv6EnableSettingDialog() {
        MaterialAlertDialogBuilder(this@SettingsSplitTunnelIpsActivity)
            .setTitle(R.string.settings_split_tunneling_ipv6_disabled_dialog_title)
            .setMessage(R.string.settings_split_tunneling_ipv6_disabled_dialog_message)
            .setNegativeButton(R.string.ok, null)
            .setPositiveButton(R.string.setting_ipv6_disabled_dialog_action_enable) { _, _ -> viewModel.onEnableIPv6() }
            .show()
    }

    private fun confirmRemove(item: LabeledItem) {
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.settings_split_tunneling_remove_ip_dialog_message)
            .setPositiveButton(R.string.remove) { _, _ -> viewModel.removeAddress(item) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    companion object {
        const val SPLIT_TUNNELING_MODE_KEY = "split tunneling mode"

        fun createContract() = createContract<SplitTunnelingMode>(SettingsSplitTunnelIpsActivity::class) { mode ->
            putExtra(SPLIT_TUNNELING_MODE_KEY, mode)
        }
    }
}

@Composable
private fun rememberIpInputState(validateIpText: (String) -> Boolean) =
    rememberSaveable(validateIpText, saver = IpInputState.createSaver(validateIpText)) {
        IpInputState(validateIpText)
    }

@VisibleForTesting
class IpInputState(
    val validateIpText: (String) -> Boolean,
    initialInputText: String = "",
    initialErrorText: String? = null,
) {
    var inputText by mutableStateOf(initialInputText)
    var errorText by mutableStateOf<String?>(initialErrorText)
    val isValid = derivedStateOf { validateIpText(inputText) }

    fun onTextChanged(new: String) {
        inputText = new
        errorText = null
    }

    fun clear() {
        inputText = ""
        errorText = null
    }

    fun setError(error: String) {
        errorText = error
    }

    companion object {
        fun createSaver(validateIpText: (String) -> Boolean): Saver<IpInputState, *> = listSaver(
            save = {
                listOf(
                    it.inputText,
                    it.errorText
                )
            },
            restore = {
                IpInputState(validateIpText,
                    initialInputText = it[0] as String,
                    initialErrorText = it[1] as String?
                )
            }
        )
    }
}

@VisibleForTesting
@Composable
fun SplitTunnelingIps(
    mode: SplitTunnelingMode,
    ipInputState: IpInputState,
    ipAddresses: List<LabeledItem>?,
    onAdd: (String) -> Unit,
    onRemove: (LabeledItem) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val titleRes = when (mode) {
        SplitTunnelingMode.INCLUDE_ONLY -> R.string.settings_split_tunneling_included_ips
        SplitTunnelingMode.EXCLUDE_ONLY -> R.string.settings_split_tunneling_excluded_ips
    }
    Scaffold(
        topBar = {
            SimpleTopAppBar(
                title = { Text(stringResource(titleRes)) },
                navigationIcon = { TopAppBarBackIcon(onBack) },
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
        Column(
            modifier = Modifier
                .padding(paddingValues)
        ) {
            IpInputTextField(
                ipInputState = ipInputState,
                onAdd = onAdd,
                modifier = Modifier
                    .fillMaxWidth()
                    .largeScreenContentPadding()
                    .padding(start = 16.dp, end = 8.dp), // Account for IconButton's padding at the end.
            )

            if (ipAddresses != null) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        val headerTextRes = when (mode) {
                            SplitTunnelingMode.INCLUDE_ONLY -> R.string.settingsIncludedIPAddressesListHeader
                            SplitTunnelingMode.EXCLUDE_ONLY -> R.string.settingsExcludedIPAddressesListHeader
                        }
                        TextSectionHeader(
                            text = stringResource(headerTextRes, ipAddresses.size),
                            modifier = Modifier
                                .largeScreenContentPadding()
                                .padding(horizontal = 16.dp)
                        )
                    }

                    items(
                        items = ipAddresses,
                        key = { it.id }
                    ) { item ->
                        LabeledItemRowWithRemove(
                            item = item,
                            onRemove = { onRemove(item) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .largeScreenContentPadding()
                                .padding(start = 16.dp, end = 8.dp)
                                .animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IpInputTextField(
    ipInputState: IpInputState,
    onAdd: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val trailingIcon = @Composable {
            if (ipInputState.inputText.isNotEmpty()) {
                IconButton(
                    onClick = { ipInputState.clear() },
                ) {
                    Icon(
                        painterResource(CoreR.drawable.ic_proton_cross),
                        tint = ProtonTheme.colors.iconWeak,
                        contentDescription = stringResource(R.string.clear_text_field_content_description)
                    )
                }
            }
        }
        val ipAddressTextStyle = ProtonTheme.typography.defaultNorm.copy(textDirection = TextDirection.Ltr)
        ProtonOutlinedTextField(
            value = ipInputState.inputText,
            onValueChange = ipInputState::onTextChanged,
            textStyle = ipAddressTextStyle,
            labelText = stringResource(R.string.inputIpAddressLabel),
            assistiveText = stringResource(R.string.inputIpAddressHelp),
            placeholderText = stringResource(R.string.inputIpAddressHintIP),
            errorText = ipInputState.errorText,
            isError = ipInputState.errorText != null,
            singleLine = true,
            trailingIcon = trailingIcon,
            keyboardActions = KeyboardActions(onDone = { onAdd(ipInputState.inputText) }),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.weight(1f),
        )

        Spacer(Modifier.width(4.dp))
        IconButton(
            onClick = { onAdd(ipInputState.inputText) },
            enabled = ipInputState.isValid.value,
        ) {
            Icon(
                painterResource(CoreR.drawable.ic_proton_plus_circle_filled),
                contentDescription = stringResource(R.string.add)
            )
        }
    }
}

@ProtonVpnPreview
@Composable
private fun SplitTunnelingIpsPreview() {
    ProtonVpnPreview {
        SplitTunnelingIps(
            mode = SplitTunnelingMode.INCLUDE_ONLY,
            ipInputState = IpInputState({ false }),
            ipAddresses = listOf(
                LabeledItem(id = "1", label = "1.2.3.4"),
                LabeledItem(id = "2", label = "2.3.4.5")
            ),
            {}, {}, {}, {}
        )
    }
}
