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

package com.protonvpn.android.ui.planupgrade

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup.MarginLayoutParams
import androidx.activity.viewModels
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.commitNow
import androidx.fragment.app.replace
import androidx.lifecycle.asLiveData
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseActivityV2
import com.protonvpn.android.databinding.ActivityUpsellDialogBinding
import com.protonvpn.android.utils.ViewUtils.toPx
import com.protonvpn.android.utils.ViewUtils.viewBinding
import com.protonvpn.android.utils.edgeToEdge
import com.protonvpn.android.utils.getSerializableExtraCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
open class UpgradeDialogActivity : BaseActivityV2() {

    protected val viewModel by viewModels<UpgradeDialogViewModel>()
    protected val binding by viewBinding(ActivityUpsellDialogBinding::inflate)

    @Inject lateinit var showUpgradeSuccess: ShowUpgradeSuccess

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupEdgeToEdge()

        viewModel.setupOrchestrators(this)
        if (savedInstanceState == null) {
            initHighlightsFragment()
            initPaymentsPanelFragment()
        }

        binding.buttonClose.setOnClickListener { finish() }
        binding.buttonNotNow.setOnClickListener { finish() }

        viewModel.state.asLiveData().observe(this) { state ->
            when (state) {
                CommonUpgradeDialogViewModel.State.Initializing -> {}
                CommonUpgradeDialogViewModel.State.UpgradeDisabled -> {}
                CommonUpgradeDialogViewModel.State.LoadingPlans -> {}
                is CommonUpgradeDialogViewModel.State.LoadError -> {}
                is CommonUpgradeDialogViewModel.State.PlanLoaded -> {}
                CommonUpgradeDialogViewModel.State.PlansFallback -> {}
                is CommonUpgradeDialogViewModel.State.PurchaseSuccess -> {
                    showUpgradeSuccess.showPlanUpgradeSuccess(this, state.newPlanName, refreshVpnInfo = true)
                    setResult(Activity.RESULT_OK)
                    finish()
                }
            }
        }
    }

    private fun initHighlightsFragment() {
        val highlightsFragmentClass: Class<out Fragment>? =
            intent.getSerializableExtraCompat<Class<out Fragment>>(FRAGMENT_CLASS_EXTRA)
        val highlightsFragmentArgs = intent.getBundleExtra(FRAGMENT_ARGS_EXTRA)

        if (highlightsFragmentClass != null) {
            supportFragmentManager.commitNow {
                add(R.id.fragmentContent, highlightsFragmentClass, highlightsFragmentArgs)
            }
            val fragment = binding.fragmentContent.getFragment<FragmentWithUpgradeSource>()
            viewModel.reportUpgradeFlowStart(fragment.upgradeSource)
        }
    }

    private fun initPaymentsPanelFragment() {
        supportFragmentManager.commitNow {
            setReorderingAllowed(true)
            replace<PaymentPanelFragment>(R.id.payment_panel_fragment)
        }
    }

    private fun setupEdgeToEdge() = with(binding) {
        edgeToEdge(root) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            fragmentContent.updatePadding(top = 24.toPx() + insets.top)
            buttonClose.updateLayoutParams<MarginLayoutParams> { topMargin = 8.toPx() + insets.top }
            root.updatePadding(bottom = 16.toPx() + insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }

    companion object {
        const val FRAGMENT_CLASS_EXTRA = "highlights fragment"
        const val FRAGMENT_ARGS_EXTRA = "highlights fragment args"

        inline fun <reified Activity :UpgradeDialogActivity, reified Fragment : FragmentWithUpgradeSource> createIntent(
            context: Context,
            args: Bundle? = null
        ) = Intent(context, Activity::class.java).apply {
            putExtra(FRAGMENT_CLASS_EXTRA, Fragment::class.java)
            putExtra(FRAGMENT_ARGS_EXTRA, args)
        }

        inline fun <reified Activity : UpgradeDialogActivity, reified Fragment : FragmentWithUpgradeSource> launchActivity(
            context: Context,
            args: Bundle? = null
        ) {
            context.startActivity(createIntent<Activity, Fragment>(context, args))
        }

        inline fun <reified Fragment : FragmentWithUpgradeSource> launch(context: Context, args: Bundle? = null)
            = launchActivity<UpgradeDialogActivity, Fragment>(context, args)

        // For Java code:
        @JvmStatic
        fun launchSecureCore(context: Context) = launch<UpgradeSecureCoreHighlightsFragment>(context, null)

        @JvmStatic
        fun launchCountry(context: Context, countryCode: String) =
            launch<UpgradeCountryHighlightsFragment>(context, UpgradeCountryHighlightsFragment.args(countryCode))
    }
}

@AndroidEntryPoint
class UpgradeOnboardingDialogActivity : UpgradeDialogActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding.buttonClose.isVisible = false
        binding.buttonNotNow.isVisible = true
    }

    companion object {
        fun launch(context: Context, args: Bundle? = null) =
            launchActivity<UpgradeOnboardingDialogActivity, UpgradeHighlightsCarouselFragment>(context, args)
    }
}
