/*
 * Copyright (c) 2021 Proton AG
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
import android.view.View
import androidx.activity.viewModels
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.commitNow
import androidx.fragment.app.viewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseActivityV2
import com.protonvpn.android.databinding.ActivityCongratsPlanBinding
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.HtmlTools
import com.protonvpn.android.utils.ViewUtils.toPx
import com.protonvpn.android.utils.ViewUtils.viewBinding
import com.protonvpn.android.utils.edgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import me.proton.core.presentation.R as CoreR

@AndroidEntryPoint
class CongratsPlanActivity : BaseActivityV2() {

    private val viewModel by viewModels<CongratsPlanViewModel>()
    private val binding by viewBinding(ActivityCongratsPlanBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        val planName = intent.getStringExtra(EXTRA_NEW_PLAN)
        if (planName == null) {
            finish()
            return
        }

        setupEdgeToEdge()
        if (savedInstanceState == null) {
            initHighlights(planName)
        }

        val button = binding.buttonGetStarted
        button.setOnClickListener { finish() }

        val shouldRefresh = intent.getBooleanExtra(EXTRA_REFRESH_VPN_USER, false)
        if (shouldRefresh) {
            button.setLoading()
            viewModel.refreshPlan()
            viewModel.state
                .flowWithLifecycle(lifecycle)
                .onEach { state ->
                    when (state) {
                        is CongratsPlanViewModel.State.Error -> {
                            snackbarHelper.errorSnack(state.message ?: getString(R.string.something_went_wrong))
                            button.isEnabled = false
                        }
                        CongratsPlanViewModel.State.Processing ->
                            button.setLoading()
                        CongratsPlanViewModel.State.Success -> {
                            button.setIdle()
                            button.isEnabled = true
                        }
                    }
                }
                .launchIn(lifecycleScope)
        }
    }

    private fun initHighlights(planName: String) {
        val fragment = when (planName) {
            "vpnplus", Constants.CURRENT_PLUS_PLAN -> CongratsPlusHighlightsFragment()
            Constants.CURRENT_BUNDLE_PLAN -> CongratsUnlimitedHighlightsFragment()
            else -> CongratsGenericHighlightsFragment()
        }
        supportFragmentManager.commitNow {
            add(R.id.fragmentContent, fragment)
        }
    }

    private fun setupEdgeToEdge() = with(binding) {
        edgeToEdge(root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            fragmentContent.updatePadding(top = 24.toPx() + insets.top)
            view.updatePadding(bottom = 16.toPx() + insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
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

@AndroidEntryPoint
class CongratsPlusHighlightsFragment : PlanHighlightsFragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.set(
            imageResource = R.drawable.welcome_plus,
            title = getString(R.string.welcome_plus_title),
            message = getString(R.string.welcome_plus_description),
        ) {
            val roundedServerCount = viewModel.allServersCount() / 100 * 100
            val countriesCount = viewModel.countriesCount()
            val serverCountText = resources.getQuantityString(
                R.plurals.welcome_plus_servers, roundedServerCount, roundedServerCount)
            val countriesCountText = resources.getQuantityString(
                R.plurals.welcome_plus_countries, countriesCount, countriesCount)
            addFeature(getString(R.string.welcome_plus_feature_servers_count, serverCountText, countriesCountText), CoreR.drawable.ic_proton_globe)
            addFeature(R.string.welcome_plus_feature_security, CoreR.drawable.ic_proton_sliders)
            addFeature(
                resources.getQuantityString(R.plurals.welcome_plus_feature_devices, 10, 10),
                CoreR.drawable.ic_proton_locks
            )
        }
    }
}

@AndroidEntryPoint
class CongratsUnlimitedHighlightsFragment : PlanHighlightsFragment() {

    private val congratsViewModel by viewModels<CongratsPlanViewModel>(ownerProducer = { requireActivity() })

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            val storageGb = congratsViewModel.getStorageGBs()
            binding.set(
                imageResource = R.drawable.welcome_unlimited,
                title = getString(R.string.welcome_unlimited_title),
                message = HtmlTools.fromHtml(getString(R.string.welcome_unlimited_description, storageGb)),
            )
        }
    }
}

@AndroidEntryPoint
class CongratsGenericHighlightsFragment : PlanHighlightsFragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.set(
            imageResource = R.drawable.welcome_generic_vpn,
            title = getString(R.string.welcome_generic_title),
            message = getString(R.string.welcome_generic_description),
        )
    }
}
