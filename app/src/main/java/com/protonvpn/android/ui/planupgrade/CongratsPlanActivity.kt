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
import androidx.lifecycle.Observer
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseActivityV2
import com.protonvpn.android.databinding.ActivityOnboardingPlanCongratsBinding
import com.protonvpn.android.models.profiles.Profile
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import me.proton.core.presentation.utils.onClick
import me.proton.core.presentation.utils.viewBinding

@AndroidEntryPoint
class CongratsPlanActivity : BaseActivityV2() {

    private val viewModel by viewModels<CongratsPlanViewModel>()
    private val binding by viewBinding(ActivityOnboardingPlanCongratsBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        viewModel.refreshPlan()
        viewModel.state.asLiveData().observe(this, Observer { state ->
            when (state) {
                is CongratsPlanViewModel.State.Error -> {
                    snackbarHelper.errorSnack(state.message ?: getString(R.string.something_went_wrong))
                    binding.connect.isEnabled = false
                }
                CongratsPlanViewModel.State.Processing ->
                    binding.connect.setLoading()
                CongratsPlanViewModel.State.Success -> {
                    binding.connect.setIdle()
                    binding.connect.isEnabled = true
                }
            }
        })

        val serverCount = viewModel.serverCount
        val roundedServerCount = serverCount / 100 * 100
        val servers = resources.getQuantityString(
            R.plurals.upgrade_plus_secure_servers,
            roundedServerCount,
            roundedServerCount
        )
        binding.description.text = getString(R.string.onboarding_plan_congrats_description, servers)

        binding.connect.onClick { connect() }
        binding.skip.onClick { finish() }
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
