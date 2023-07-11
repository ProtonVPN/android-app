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
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.protonvpn.android.R
import com.protonvpn.android.databinding.FragmentNetshieldComposeViewBinding
import com.protonvpn.android.ui.home.vpn.VpnStateViewModel
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.HtmlTools
import com.protonvpn.android.utils.ViewUtils.toPx
import dagger.hilt.android.AndroidEntryPoint
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.default
import me.proton.core.compose.theme.defaultSmall

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
            val currentNetShieldValue = parentViewModel.getCurrentNetShield().collectAsStateWithLifecycle(null).value
            if (currentNetShieldValue != null) {
                NetShieldBottomComposable(currentNetShieldValue) {
                    parentViewModel.setNetShieldProtocol(it)
                    dismiss()
                }
            }
        }
        return binding.root
    }

    @Composable
    private fun NetShieldBottomComposable(
        currentNetShield: NetShieldProtocol,
        onValueChanged: (protocol: NetShieldProtocol) -> Unit
    ) {
        ProtonTheme {
            Column(
                Modifier.background(
                    ProtonTheme.colors.backgroundNorm
                )
            ) {
                Text(
                    modifier = Modifier.padding(16.dp, 20.dp, 16.dp, 8.dp),
                    text = stringResource(R.string.netshield_feature_name),
                    style = ProtonTheme.typography.default,
                )

                AndroidView(
                    factory = { context ->
                        TextView(context).apply {
                            movementMethod = LinkMovementMethod.getInstance()
                            setTextAppearance(R.style.Proton_Text_Caption_Weak)
                            setPadding(16.toPx(), 0, 16.toPx(), 16.toPx())
                            text = HtmlTools.fromHtml(
                                getString(
                                    R.string.netshield_settings_description,
                                    Constants.URL_NETSHIELD_LEARN_MORE
                                )
                            )
                        }
                    }
                )
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(ProtonTheme.colors.separatorNorm)
                )
                NetShieldProtocol.values().forEach { value ->
                    RadioButtonWithLabel(
                        label = stringResource(id = netShieldProtocolToText(value)),
                        isSelected = (value == currentNetShield),
                        onValueChanged = { onValueChanged(value) }
                    )
                }
            }
        }
    }

    @Composable
    fun RadioButtonWithLabel(
        label: String,
        isSelected: Boolean,
        onValueChanged: () -> Unit
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .selectable(
                    selected = isSelected,
                    onClick = onValueChanged
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = ProtonTheme.typography.defaultSmall,
                textAlign = TextAlign.Start,
                modifier = Modifier.weight(1f)
            )
            RadioButton(
                selected = isSelected,
                onClick = null
            )
        }
    }

    @Preview
    @Composable
    private fun GenericRadioPreview() {
        RadioButtonWithLabel(
            label = "Nothing",
            isSelected = true,
            onValueChanged = {}
        )
    }

    @Preview
    @Composable
    private fun NetShieldBottomSheetPreview() {
        NetShieldBottomComposable(
            currentNetShield = NetShieldProtocol.DISABLED,
            onValueChanged = {}
        )
    }

    private fun netShieldProtocolToText(netShieldProtocol: NetShieldProtocol) =
        when (netShieldProtocol) {
            NetShieldProtocol.DISABLED -> R.string.netshield_status_off
            NetShieldProtocol.ENABLED -> R.string.netShieldBlockMalwareOnly
            NetShieldProtocol.ENABLED_EXTENDED -> R.string.netShieldFullBlock
        }
}
