package com.protonvpn.android.ui

import androidx.lifecycle.ViewModel
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.vpn.ProtocolSelection
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ProtocolSelectionViewModel @Inject constructor(
    val appConfig: AppConfig,
) : ViewModel() {

    private val items = listOf(
        ProtocolSelection(VpnProtocol.Smart),
        ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.UDP),
        ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.TCP),
        ProtocolSelection(VpnProtocol.OpenVPN, TransmissionProtocol.UDP),
        ProtocolSelection(VpnProtocol.OpenVPN, TransmissionProtocol.TCP),
        ProtocolSelection(VpnProtocol.IKEv2),
        ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.TLS),
    )

    val supportedProtocols = items.filter { it.isSupported(appConfig.getFeatureFlags()) }
}
