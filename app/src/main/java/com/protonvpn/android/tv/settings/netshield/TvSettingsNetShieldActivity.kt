/*
 * Copyright (c) 2025. Proton AG
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

package com.protonvpn.android.tv.settings.netshield

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseTvActivity
import com.protonvpn.android.redesign.base.ui.collectAsEffect
import com.protonvpn.android.tv.drawers.TvModalDrawer
import com.protonvpn.android.tv.settings.TvSettingsItemMoreInfo
import com.protonvpn.android.tv.settings.TvSettingsMainToggleLayout
import com.protonvpn.android.tv.settings.TvSettingsMainWarningBanner
import com.protonvpn.android.tv.settings.TvSettingsMoreInfoLayout
import com.protonvpn.android.tv.settings.TvSettingsReconnectDialog
import com.protonvpn.android.tv.ui.TvUiConstants
import com.protonvpn.android.utils.openWifiSettings
import com.protonvpn.android.vpn.DnsOverride
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.receiveAsFlow
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.presentation.compose.tv.theme.ProtonThemeTv

@AndroidEntryPoint
class TvSettingsNetShieldActivity : BaseTvActivity() {

    // TODO: see if there is some common part that can be extracted for the TvSettings* activities.

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ProtonThemeTv {
                val viewModel: TvSettingsNetShieldViewModel = hiltViewModel()
                val viewState = viewModel.viewState.collectAsStateWithLifecycle().value
                var isDrawerOpen by remember { mutableStateOf(false) }
                var showReconnectDialog by remember { mutableStateOf(false) }

                BackHandler(enabled = isDrawerOpen) {
                    isDrawerOpen = false
                }

                viewModel.eventChannelReceiver.receiveAsFlow().collectAsEffect { event ->
                    when (event) {
                        TvSettingsNetShieldViewModel.Event.OnClose -> {
                            finish()
                        }

                        TvSettingsNetShieldViewModel.Event.OnDismissReconnectNowDialog -> {
                            showReconnectDialog = false
                        }

                        TvSettingsNetShieldViewModel.Event.OnShowReconnectNowDialog -> {
                            viewModel.onShowReconnectNowDialog(vpnUiDelegate = getVpnUiDelegate())

                            showReconnectDialog = true
                        }
                    }
                }

                if (viewState != null) {
                    TvModalDrawer(
                        isDrawerOpen = isDrawerOpen,
                        drawerContent = {
                            TvSettingsMoreInfoLayout(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 32.dp),
                                title = stringResource(id = R.string.netshield_settings_more_info_title_tv),
                                paragraphs = stringArrayResource(id = R.array.netshield_settings_more_info_paragraphs_tv),
                            )
                        },
                        content = {
                            when (viewState.dnsOverride) {
                                DnsOverride.None -> {
                                    TvSettingsNetShield(
                                        modifier = Modifier.fillMaxWidth(),
                                        viewState = viewState,
                                        onToggled = viewModel::toggleNetShield,
                                        onLearnMoreClicked = { isDrawerOpen = true },
                                    )
                                }

                                DnsOverride.CustomDns -> {
                                    TvSettingsNetShieldConflict(
                                        modifier = Modifier.fillMaxWidth(),
                                        titleResId = R.string.custom_dns_conflict_banner_netshield_title_tv,
                                        descriptionResId = R.string.custom_dns_conflict_banner_netshield_description_tv,
                                        actionResId = R.string.custom_dns_conflict_banner_disable_custom_dns_button,
                                        onConflictActionClicked = viewModel::disableCustomDns,
                                    )
                                }

                                DnsOverride.SystemPrivateDns -> {
                                    TvSettingsNetShieldConflict(
                                        modifier = Modifier.fillMaxWidth(),
                                        titleResId = R.string.custom_dns_conflict_banner_netshield_title_tv,
                                        descriptionResId = R.string.private_dns_conflict_banner_custom_dns_description_tv,
                                        actionResId = R.string.private_dns_conflict_banner_network_settings_button,
                                        onConflictActionClicked = { openWifiSettings(isTv = true) },
                                    )
                                }
                            }
                        }
                    )
                }

                if(showReconnectDialog) {
                    TvSettingsReconnectDialog(
                        onReconnectNow = {
                            viewModel.onReconnectNow(vpnUiDelegate = getVpnUiDelegate())
                        },
                        onDismissRequest = viewModel::onDismissReconnectNowDialog,
                    )
                }
            }
        }
    }
}

@Composable
private fun TvSettingsNetShield(
    viewState: TvSettingsNetShieldViewModel.ViewState,
    onToggled: () -> Unit,
    onLearnMoreClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.TopCenter,
    ) {
        TvSettingsMainToggleLayout(
            modifier = Modifier.widthIn(max = TvUiConstants.SingleColumnWidth),
            title = stringResource(id = R.string.settings_netshield_title),
            titleImageRes = R.drawable.tv_settings_netshield_header_image,
            toggleLabel = stringResource(id = R.string.settings_netshield_title),
            toggleValue = viewState.isNetShieldEnabled,
            onToggled = onToggled,
        ) {
            item {
                Text(
                    text = stringResource(id = R.string.netshield_settings_description_tv),
                    style = ProtonTheme.typography.body2Regular,
                    color = ProtonTheme.colors.textWeak,
                    modifier = Modifier
                        .padding(horizontal = TvUiConstants.SelectionPaddingHorizontal)
                        .padding(top = 12.dp, bottom = 16.dp)
                )
            }

            item {
                TvSettingsItemMoreInfo(
                    text = stringResource(id = R.string.dialogLearnMore),
                    onClick = onLearnMoreClicked,
                )
            }
        }
    }
}

@Composable
private fun TvSettingsNetShieldConflict(
    @StringRes titleResId: Int,
    @StringRes descriptionResId: Int,
    @StringRes actionResId: Int,
    onConflictActionClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.TopCenter,
    ) {
        TvSettingsMainWarningBanner(
            modifier = Modifier.widthIn(max = TvUiConstants.SingleColumnWidth),
            headerImageRes = R.drawable.tv_settings_netshield_header_image,
            headerTitle = stringResource(id = R.string.settings_netshield_title),
            headerDescription = stringResource(id = R.string.netshield_settings_description_tv),
            bannerTitle = stringResource(id = titleResId),
            bannerDescription = stringResource(id = descriptionResId),
            actionText = stringResource(id = actionResId),
            onActionClicked = onConflictActionClicked,
        )
    }
}
