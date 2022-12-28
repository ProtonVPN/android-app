/*
 * Copyright (c) 2021. Proton Technologies AG
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

package com.protonvpn.android.ui.home.vpn

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.asLiveData
import com.protonvpn.android.R
import com.protonvpn.android.components.VpnUiDelegateProvider
import com.protonvpn.android.databinding.FragmentVpnStateErrorBinding
import com.protonvpn.android.ui.login.TroubleshootActivity
import com.protonvpn.android.utils.HtmlTools
import com.protonvpn.android.vpn.DisconnectTrigger
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class VpnStateErrorFragment : Fragment(R.layout.fragment_vpn_state_error) {

    private val viewModel: VpnStateErrorViewModel by viewModels()
    private val parentViewModel: VpnStateViewModel by viewModels(ownerProducer = { requireParentFragment() })

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentVpnStateErrorBinding.bind(view)

        with(binding) {
            buttonRetry.setOnClickListener {
                parentViewModel.reconnect(
                    (requireActivity() as VpnUiDelegateProvider).getVpnUiDelegate())
            }
            buttonCancelRetry.setOnClickListener {
                parentViewModel.disconnect(DisconnectTrigger.ConnectionPanel("cancel automatic retry"))
            }
            buttonHelp.setOnClickListener {
                startActivity(Intent(requireContext(), TroubleshootActivity::class.java))
            }
        }

        viewModel.errorMessage.asLiveData().observe(
            viewLifecycleOwner, Observer { binding.textError.text = HtmlTools.fromHtml(it) }
        )
    }
}
