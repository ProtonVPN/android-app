/*
 * Copyright (c) 2023. Proton Technologies AG
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
package com.protonvpn.android.netshield

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.AnnotatedClickableText
import com.protonvpn.android.base.ui.theme.VpnTheme
import com.protonvpn.android.databinding.FragmentNetshieldComposeViewBinding
import com.protonvpn.android.ui.home.vpn.VpnStateViewModel
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.openUrl
import dagger.hilt.android.AndroidEntryPoint
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.captionStrongNorm
import me.proton.core.compose.theme.captionWeak
import me.proton.core.compose.theme.defaultNorm

@Deprecated("Fragment implementation to be removed once there is no backward compatibility need")
@AndroidEntryPoint
class BottomSheetNetShield : BottomSheetDialogFragment() {

    private val parentViewModel: VpnStateViewModel by viewModels(ownerProducer = { requireParentFragment() })

    private lateinit var binding: FragmentNetshieldComposeViewBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentNetshieldComposeViewBinding.inflate(inflater, container, false)
        binding.composeContainer.setContent {
            VpnTheme {
                val currentNetShieldValue =
                    parentViewModel.getCurrentNetShield().collectAsStateWithLifecycle(null).value
                if (currentNetShieldValue != null) {
                    NetShieldBottomComposable(currentNetShieldValue, onValueChanged = {
                        parentViewModel.setNetShieldProtocol(it)
                        dismiss()
                    }, {
                        requireContext().openUrl(Constants.URL_NETSHIELD_LEARN_MORE)
                    })
                }
            }
        }
        return binding.root
    }
}

@Composable
fun NetShieldBottomComposable(
    currentNetShield: NetShieldProtocol,
    onValueChanged: (protocol: NetShieldProtocol) -> Unit,
    onNetShieldLearnMore: () -> Unit
) {
    val switchState = remember {
        mutableStateOf(currentNetShield != NetShieldProtocol.DISABLED)
    }

    Column(
        modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(id = R.string.settings_netshield_title),
                style = ProtonTheme.typography.defaultNorm,
                modifier = Modifier.weight(1f)
            )
            Switch(checked = switchState.value, onCheckedChange = {
                switchState.value = it
                onValueChanged(if (it) NetShieldProtocol.ENABLED_EXTENDED else NetShieldProtocol.DISABLED)
            })

        }
        AnnotatedClickableText(
            fullText = stringResource(id = R.string.netshield_settings_description_not_html, stringResource(
                id = R.string.learn_more
            )),
            annotatedPart = stringResource(id = R.string.learn_more),
            onAnnotatedClick = onNetShieldLearnMore,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Surface(
            shape = RoundedCornerShape(size = 8.dp),
            color = Color.Transparent,
            border = BorderStroke(1.dp, ProtonTheme.colors.textDisabled),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.netshield_what_data_means),
                    style = ProtonTheme.typography.defaultNorm,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                StatsDescriptionRows(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun StatsDescriptionRows(modifier: Modifier) {
    Column(modifier = modifier) {
        StatsDescriptionRow(
            titleId = R.string.netshield_ads_title,
            detailsId = R.string.netshield_ads_details
        )
        StatsDescriptionRow(
            titleId = R.string.netshield_trackers_title,
            detailsId = R.string.netshield_trackers_details
        )
        StatsDescriptionRow(
            titleId = R.string.netshield_data_title,
            detailsId = R.string.netshield_data_details
        )
    }
}

@Composable
private fun StatsDescriptionRow(titleId: Int, detailsId: Int) {
    Row(modifier = Modifier.padding(8.dp)) {
        Box(modifier = Modifier.weight(0.7f, fill = true)) {
            Text(
                text = stringResource(id = titleId),
                style = ProtonTheme.typography.captionStrongNorm
            )
        }
        Text(
            text = stringResource(id = detailsId),
            style = ProtonTheme.typography.captionWeak,
            modifier = Modifier.weight(2f)
        )
    }
}

@Preview
@Composable
private fun NetShieldBottomPreview() {
    NetShieldBottomComposable(
        currentNetShield = NetShieldProtocol.DISABLED,
        onValueChanged = {},
        onNetShieldLearnMore = {}
    )
}

@Preview
@Composable
private fun NetShieldBottomSheetPreview() {
    NetShieldBottomComposable(
        currentNetShield = NetShieldProtocol.DISABLED,
        onValueChanged = {},
        onNetShieldLearnMore = {}
    )
}