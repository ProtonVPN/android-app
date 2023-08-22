/*
 * Copyright (c) 2021 Proton Technologies AG
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

package com.protonvpn.android.ui.planupgrade

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseActivityV2
import com.protonvpn.android.databinding.ActivityUpsellDialogBinding
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.utils.ViewUtils.viewBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CongratsPlanActivity : BaseActivityV2() {

    private val viewModel by viewModels<CongratsPlanViewModel>()
    private val binding by viewBinding(ActivityUpsellDialogBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.initBinding(
            this,
            imageResource = R.drawable.welcome_plus,
            title = getString(R.string.welcome_plus_title),
            message = getString(R.string.welcome_plus_description),
            mainButtonLabel = R.string.onboading_connect_to_plus,
            mainButtonAction = ::connect,
            otherButtonLabel = R.string.got_it,
        ) {
            val roundedServerCount = viewModel.serverCount / 100 * 100
            val countriesCount = viewModel.countriesCount
            val serverCountText = resources.getQuantityString(
                R.plurals.welcome_plus_servers, roundedServerCount, roundedServerCount)
            val countriesCountText = resources.getQuantityString(
                R.plurals.welcome_plus_countries, countriesCount, countriesCount)
            addFeature(getString(R.string.welcome_plus_feature_servers_count, serverCountText, countriesCountText), R.drawable.ic_proton_globe)
            addFeature(R.string.welcome_plus_feature_security, R.drawable.ic_proton_sliders)
            addFeature(
                resources.getQuantityString(R.plurals.welcome_plus_feature_devices, 10, 10),
                R.drawable.ic_proton_locks
            )
        }

        val connectButton = binding.buttonMainAction
        connectButton.setLoading()
        viewModel.refreshPlan()
        viewModel.state.asLiveData().observe(this) { state ->
            when (state) {
                is CongratsPlanViewModel.State.Error -> {
                    snackbarHelper.errorSnack(state.message
                        ?: getString(R.string.something_went_wrong))
                    connectButton.isEnabled = false
                }
                CongratsPlanViewModel.State.Processing ->
                    connectButton.setLoading()
                CongratsPlanViewModel.State.Success -> {
                    connectButton.setIdle()
                    connectButton.isEnabled = true
                }
            }
        }
    }

    private fun connect() =
        lifecycleScope.launch {
            if (viewModel.connectPlus(this@CongratsPlanActivity, getVpnUiDelegate()))
                finish()
        }

    override fun retryConnection(profile: Profile) {
        connect()
    }

    companion object {
        fun create(context: Context) = Intent(context, CongratsPlanActivity::class.java)
    }
}
