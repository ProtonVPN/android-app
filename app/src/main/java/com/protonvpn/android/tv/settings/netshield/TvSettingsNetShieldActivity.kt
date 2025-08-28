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
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseTvActivity
import com.protonvpn.android.tv.settings.TvSettingsMainToggleLayout
import com.protonvpn.android.tv.ui.TvUiConstants
import dagger.hilt.android.AndroidEntryPoint
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

                Box(
                    contentAlignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (viewState != null) {
                        TvSettingsNetShield(
                            viewState = viewState,
                            onToggled = viewModel::toggleNetShield,
                            modifier = Modifier.widthIn(max = TvUiConstants.SingleColumnWidth),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvSettingsNetShield(
    viewState: TvSettingsNetShieldViewModel.ViewState,
    onToggled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvSettingsMainToggleLayout(
        modifier = modifier,
        title = stringResource(R.string.settings_netshield_title),
        titleImageRes = R.drawable.tv_settings_netshield_header_image,
        toggleLabel = stringResource(R.string.settings_netshield_title),
        toggleValue = viewState.isEnabled,
        onToggled = onToggled,
    ) {
        item {
            Text(
                stringResource(R.string.netshield_settings_description_tv),
                style = ProtonTheme.typography.body2Regular,
                color = ProtonTheme.colors.textWeak,
                modifier = Modifier
                    .padding(horizontal = TvUiConstants.SelectionPaddingHorizontal)
                    .padding(top = 12.dp, bottom = 16.dp)
            )
        }

        // TODO(VPNAND-2338): info item (opens side dialog)
    }
}
