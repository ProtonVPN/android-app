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
import com.protonvpn.android.utils.HtmlTools
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
        val planName = intent.getStringExtra(EXTRA_NEW_PLAN)
        if (planName == null) {
            finish()
            return
        }

        initUi(planName)

        val shouldRefresh = intent.getBooleanExtra(EXTRA_REFRESH_VPN_USER, false)
        if (shouldRefresh) {
            val mainButton = binding.buttonMainAction
            mainButton.setLoading()
            viewModel.refreshPlan()
            viewModel.state.asLiveData().observe(this) { state ->
                when (state) {
                    is CongratsPlanViewModel.State.Error -> {
                        snackbarHelper.errorSnack(state.message?: getString(R.string.something_went_wrong))
                        mainButton.isEnabled = false
                    }
                    CongratsPlanViewModel.State.Processing ->
                        mainButton.setLoading()
                    CongratsPlanViewModel.State.Success -> {
                        mainButton.setIdle()
                        mainButton.isEnabled = true
                    }
                }
            }
        }
    }

    private fun initUi(planName: String) {
        when (planName) {
            "vpnplus", "vpn2022" -> initPlusUi()
            "bundle2022" -> initUnlimitedUi()
            else -> initGenericUi()
        }
    }

    private fun initPlusUi() {
        binding.initBinding(
            this,
            imageResource = R.drawable.welcome_plus,
            title = getString(R.string.welcome_plus_title),
            message = getString(R.string.welcome_plus_description),
            mainButtonLabel = R.string.upgrade_success_get_started_button,
            mainButtonAction = ::dismiss,
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
    }

    private fun initUnlimitedUi() {
        lifecycleScope.launch {
            val storageGb = viewModel.getStorageGBs()
            binding.initBinding(
                this@CongratsPlanActivity,
                imageResource = R.drawable.welcome_unlimited,
                title = getString(R.string.welcome_unlimited_title),
                message = HtmlTools.fromHtml(getString(R.string.welcome_unlimited_description, storageGb)),
                mainButtonLabel = R.string.upgrade_success_get_started_button,
                mainButtonAction = ::dismiss,
            )
        }
    }

    private fun initGenericUi() {
        binding.initBinding(
            this,
            imageResource = R.drawable.welcome_generic_vpn,
            title = getString(R.string.welcome_generic_title),
            message = getString(R.string.welcome_generic_description),
            mainButtonLabel = R.string.upgrade_success_get_started_button,
            mainButtonAction = ::dismiss,
        )
    }

    private fun dismiss() {
        finish()
    }

    companion object {
        private const val EXTRA_REFRESH_VPN_USER = "refresh vpn user"
        private const val EXTRA_NEW_PLAN = "new plan"

        fun createIntent(context: Context, planName: String, refreshVpnInfo: Boolean): Intent =
            Intent(context, CongratsPlanActivity::class.java).apply {
                putExtra(EXTRA_REFRESH_VPN_USER, refreshVpnInfo)
                putExtra(EXTRA_NEW_PLAN, planName)
            }
    }
}
