/*
 * Copyright (c) 2020 Proton AG
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
package com.protonvpn.android.tv

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import com.protonvpn.android.R
import com.protonvpn.android.databinding.TvStatusViewBinding
import com.protonvpn.android.tv.main.TvMainViewModel
import com.protonvpn.android.utils.HtmlTools
import com.protonvpn.android.vpn.DisconnectTrigger
import com.protonvpn.android.vpn.ErrorType
import com.protonvpn.android.vpn.VpnState
import dagger.hilt.android.AndroidEntryPoint
import me.proton.core.util.kotlin.exhaustive
import me.proton.core.util.kotlin.takeIfNotBlank
import me.proton.core.presentation.R as CoreR

@AndroidEntryPoint
class TvStatusFragment : Fragment() {
    private lateinit var binding: TvStatusViewBinding

    private lateinit var viewModel: TvMainViewModel
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = TvStatusViewBinding.inflate(inflater, container, false)
        viewModel = ViewModelProvider(requireActivity()).get(TvMainViewModel::class.java)
        viewModel.vpnViewState.asLiveData().observe(viewLifecycleOwner) {
            updateVpnState(it)
        }
        return binding.root
    }

    private fun updateVpnState(state: TvMainViewModel.VpnViewState) = with(binding) {
        val vpnState = state.vpnStatus.state
        val statusColor = when (vpnState) {
            VpnState.Connected -> R.color.tvAccentLighten
            is VpnState.Error -> R.color.tvAlert
            else -> CoreR.color.white
        }
        textStatus.setTextColor(ContextCompat.getColor(requireContext(), statusColor))

        val ipString = state.ipToDisplay?.takeIfNotBlank() ?: getString(R.string.stateFragmentUnknownIp)
        textIp.text = getString(R.string.ipWithPlaceholder, ipString)
        when (vpnState) {
            VpnState.Connected -> {
                textStatus.text = getString(R.string.stateConnectedTo, state.vpnStatus.server?.displayName)
            }
            VpnState.Connecting -> {
                textStatus.text = getString(R.string.state_connecting)
            }
            VpnState.Disabled -> {
                textStatus.text = getString(R.string.stateNotConnected)
            }
            VpnState.Reconnecting -> {
                textStatus.text = getString(R.string.loaderReconnecting)
            }
            VpnState.ScanningPorts, VpnState.CheckingAvailability -> {
                textStatus.text = getString(R.string.loaderCheckingAvailability)
            }
            VpnState.WaitingForNetwork -> {
                textStatus.text = getString(R.string.loaderReconnectNoNetwork)
            }
            VpnState.Disconnecting -> {
                textStatus.text = getString(R.string.loaderDisconnecting)
            }
            is VpnState.Error -> {
                onError(vpnState.type)
            }
        }.exhaustive
    }

    private fun onError(error: ErrorType) = with(binding) {
        when (error) {
            ErrorType.UNREACHABLE ->
                textStatus.setText(R.string.error_server_unreachable)
            // dialog
            ErrorType.AUTH_FAILED ->
                showErrorDialog(R.string.error_auth_failed)
            ErrorType.PEER_AUTH_FAILED ->
                showErrorDialog(R.string.error_peer_auth_failed)
            ErrorType.MAX_SESSIONS ->
                showErrorDialog(R.string.errorMaxSessions)
            ErrorType.POLICY_VIOLATION_DELINQUENT ->
                showErrorDialog(HtmlTools.fromHtml(getString(R.string.errorUserDelinquent)))
            ErrorType.MULTI_USER_PERMISSION ->
                showErrorDialog(R.string.errorTunMultiUserPermission)
            else -> {}
        }
    }

    private fun showErrorDialog(@StringRes stringRes: Int) {
        showErrorDialog(getString(stringRes))
    }

    private fun showErrorDialog(content: CharSequence) {
        viewModel.disconnect(DisconnectTrigger.Error("status (TV)"))
        showTvDialog(requireContext(), focusedButton = DialogInterface.BUTTON_NEGATIVE) {
            setTitle(R.string.tv_vpn_error_dialog_title)
            setMessage(content)
            setCancelable(false)
            setNegativeButton(R.string.close, null)
        }
    }
}
