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
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import androidx.activity.viewModels
import androidx.annotation.CallSuper
import androidx.collection.ArraySet
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.commitNow
import androidx.fragment.app.replace
import androidx.lifecycle.asLiveData
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.theme.LightAndDarkPreview
import com.protonvpn.android.base.ui.theme.VpnTheme
import com.protonvpn.android.components.BaseActivityV2
import com.protonvpn.android.databinding.ActivityUpsellDialogBinding
import com.protonvpn.android.telemetry.UpgradeSource
import com.protonvpn.android.ui.onboarding.OnboardingTelemetry
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.DebugUtils
import com.protonvpn.android.utils.ViewUtils.toPx
import com.protonvpn.android.utils.ViewUtils.viewBinding
import com.protonvpn.android.utils.edgeToEdge
import com.protonvpn.android.utils.getSerializableExtraCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlin.reflect.KClass
import me.proton.core.presentation.R as CoreR

abstract class BaseUpgradeDialogActivity(private val allowMultiplePlans: Boolean) : BaseActivityV2() {

    protected val viewModel by viewModels<UpgradeDialogViewModel>()
    protected val binding by viewBinding(ActivityUpsellDialogBinding::inflate)
    private val carouselViewModel: UpgradeHighlightsCarouselViewModel by viewModels()

    private lateinit var backgroundGradient: GradientDrawable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupEdgeToEdge()
        setupGradientBackground()

        viewModel.setupOrchestrators(this)
        if (savedInstanceState == null) {
            initHighlightsFragment()
            initPaymentsPanelFragment()
            viewModel.loadPlans(allowMultiplePlans)
            viewModel.reportUpgradeFlowStart(getTelemetryUpgradeSource())
        }

        binding.buttonNotNow.setOnClickListener { finish() }

        viewModel.state.asLiveData().observe(this) { state ->
            when (state) {
                CommonUpgradeDialogViewModel.State.Initializing -> {}
                CommonUpgradeDialogViewModel.State.UpgradeDisabled -> {}
                CommonUpgradeDialogViewModel.State.LoadingPlans -> {}
                is CommonUpgradeDialogViewModel.State.LoadError -> {}
                is CommonUpgradeDialogViewModel.State.PurchaseReady -> {}
                CommonUpgradeDialogViewModel.State.PlansFallback -> {}
                is CommonUpgradeDialogViewModel.State.PurchaseSuccess -> {
                    onPaymentSuccess(state.newPlanName, state.upgradeFlowType)
                }
            }
        }

        binding.composeToolbar.setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val purchaseState = state as? CommonUpgradeDialogViewModel.State.PurchaseReady
            VpnTheme {
                CloseButtonAndPlanSelectionToolbar(
                    allPlans = purchaseState?.allPlans ?: emptyList(),
                    selectedPlan = purchaseState?.selectedPlan,
                    inProgress = purchaseState?.inProgress ?: false,
                    onClose = ::finish,
                    onPlanSelected = { plan ->
                        viewModel.selectPlan(plan)
                        onPlanSelected(plan.planName)
                    },
                    modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
                )
            }
        }

        carouselViewModel.gradientOverride
            .flowWithLifecycle(lifecycle)
            .onEach { colors ->
                if (colors != null) setGradientColors(colors.first, colors.second, colors.third)
                else setDefaultGradient()
            }
            .launchIn(lifecycleScope)
    }

    @CallSuper
    protected open fun onPaymentSuccess(newPlanName: String, upgradeFlowType: UpgradeFlowType) {
        setResult(Activity.RESULT_OK)
        finish()
    }

    protected fun setGradientColors(top: Int, mid: Int, bottom: Int) {
        backgroundGradient.colors = intArrayOf(top, mid, bottom, 0x00000000, 0x00000000)
        backgroundGradient.invalidateSelf()
    }

    protected abstract fun initHighlightsFragment()

    protected abstract fun onPlanSelected(planName: String)

    protected abstract fun getTelemetryUpgradeSource(): UpgradeSource

    private fun initPaymentsPanelFragment() {
        supportFragmentManager.commitNow {
            setReorderingAllowed(true)
            replace<PaymentPanelFragment>(R.id.payment_panel_fragment)
        }
    }

    private fun setupEdgeToEdge() = with(binding) {
        edgeToEdge(root) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            root.updatePadding(bottom = 16.toPx() + insets.bottom)
            windowInsets
        }
    }

    private fun setupGradientBackground() {
        backgroundGradient =
            GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x00000000, 0x00000000))
        binding.root.background = backgroundGradient
        setDefaultGradient()
    }

    private fun setDefaultGradient() {
        setGradientColors(0xFF2E737B.toInt(), 0x802E737B.toInt(), 0x002E737B)
    }
}

@AndroidEntryPoint
class PlusOnlyUpgradeDialogActivity : BaseUpgradeDialogActivity(allowMultiplePlans = false) {

    override fun initHighlightsFragment() {
        val highlightsFragmentClass: Class<out Fragment>? =
            intent.getSerializableExtraCompat<Class<out Fragment>>(FRAGMENT_CLASS_EXTRA)

        if (highlightsFragmentClass != null) {
            val args = intent.getBundleExtra(FRAGMENT_ARGS_EXTRA)
            supportFragmentManager.commitNow {
                add(R.id.fragmentContent, highlightsFragmentClass, args)
            }
        }
    }

    override fun onPlanSelected(planName: String) {
        DebugUtils.fail("Not supported")
    }

    override fun getTelemetryUpgradeSource(): UpgradeSource {
        // Called after initHighlightsFragment()
        val fragment = binding.fragmentContent.getFragment<FragmentWithUpgradeSource>()
        return fragment.upgradeSource
    }

    companion object {
        const val FRAGMENT_CLASS_EXTRA = "highlights fragment"
        const val FRAGMENT_ARGS_EXTRA = "fragment args"

        inline fun <reified Fragment : FragmentWithUpgradeSource> createIntent(context: Context, args: Bundle? = null) =
            Intent(context, PlusOnlyUpgradeDialogActivity::class.java).apply {
                putExtra(FRAGMENT_CLASS_EXTRA, Fragment::class.java)
                if (args != null) {
                    putExtra(FRAGMENT_ARGS_EXTRA, args)
                }
            }

        inline fun <reified Fragment : FragmentWithUpgradeSource> launch(context: Context, args: Bundle? = null) {
            context.startActivity(createIntent<Fragment>(context, args))
        }
    }
}

@AndroidEntryPoint
class CarouselUpgradeDialogActivity : BaseUpgradeDialogActivity(allowMultiplePlans = true) {

    // Detach and reattach fragments so that they retain their saved state, like carousel position.
    private val carouselFragments = ArraySet<Fragment>()

    override fun initHighlightsFragment() {
        val carouselArgs = intent.getBundleExtra(CAROUSEL_FRAGMENT_ARGS_EXTRA)
        supportFragmentManager.commitNow {
            add(R.id.fragmentContent, VPN_PLUS_CAROUSEL, carouselArgs)
        }
    }

    override fun onPlanSelected(planName: String) {
        val fragmentClass = when (planName) {
            Constants.CURRENT_PLUS_PLAN -> VPN_PLUS_CAROUSEL
            Constants.CURRENT_BUNDLE_PLAN -> BUNDLE_CAROUSEL
            else -> null
        }
        if (fragmentClass != null) {
            supportFragmentManager.commitNow {
                val currentFragment = binding.fragmentContent.getFragment<Fragment?>()
                if (currentFragment != null) {
                    detach(currentFragment)
                    carouselFragments.add(currentFragment)
                }
                val existingFragment = carouselFragments.find { it::class.java == fragmentClass }
                if (existingFragment != null) {
                    attach(existingFragment)
                } else {
                    add(R.id.fragmentContent, fragmentClass, null)
                }
            }
        } else {
            DebugUtils.fail("No highlights fragment for selected plan!")
        }
    }

    override fun getTelemetryUpgradeSource(): UpgradeSource =
        requireNotNull(
            intent.getSerializableExtraCompat<UpgradeSource>(UPGRADE_SOURCE_EXTRA)
                ?: getUpgradeSourceFromFocusedFragment()
        )

    private fun getUpgradeSourceFromFocusedFragment(): UpgradeSource? {
        val carouselArgs = intent.getBundleExtra(CAROUSEL_FRAGMENT_ARGS_EXTRA)
        val fragment = UpgradeHighlightsCarouselFragment.focusedFragment(carouselArgs)?.newInstance()
        return (fragment as? FragmentWithUpgradeSource)?.upgradeSource
    }

    companion object {
        private val VPN_PLUS_CAROUSEL = UpgradeCarouselVpnPlusHighlightsFragment::class.java
        private val BUNDLE_CAROUSEL = UpgradeCarouselUnlimitedHighlightsFragment::class.java
        const val UPGRADE_SOURCE_EXTRA = "upgrade source"
        const val CAROUSEL_FRAGMENT_ARGS_EXTRA = "carousel args"

        inline fun <reified F : FragmentWithUpgradeSource> createIntent(context: Context) =
            Intent(context, CarouselUpgradeDialogActivity::class.java).apply {
                putExtra(CAROUSEL_FRAGMENT_ARGS_EXTRA, UpgradeHighlightsCarouselFragment.args(F::class))
            }

        inline fun <reified F : Fragment> createIntent(context: Context, upgradeSource: UpgradeSource) =
            Intent(context, CarouselUpgradeDialogActivity::class.java).apply {
                putExtra(UPGRADE_SOURCE_EXTRA, upgradeSource)
                putExtra(CAROUSEL_FRAGMENT_ARGS_EXTRA, UpgradeHighlightsCarouselFragment.args(F::class))
            }

        inline fun <reified F : Fragment> launch(context: Context, upgradeSource: UpgradeSource) =
            context.startActivity(createIntent<F>(context, upgradeSource))

        inline fun <reified F : FragmentWithUpgradeSource> launch(context: Context) =
            context.startActivity(createIntent<F>(context))

        fun launch(context: Context, upgradeSource: UpgradeSource, focusedFragmentClass: KClass<out Fragment>? = null) {
            val intent = Intent(context, CarouselUpgradeDialogActivity::class.java).apply {
                putExtra(UPGRADE_SOURCE_EXTRA, upgradeSource)
                if (focusedFragmentClass != null) {
                    putExtra(CAROUSEL_FRAGMENT_ARGS_EXTRA, UpgradeHighlightsCarouselFragment.args(focusedFragmentClass))
                }
            }
            context.startActivity(intent)
        }
    }
}

@AndroidEntryPoint
class UpgradeOnboardingDialogActivity : BaseUpgradeDialogActivity(allowMultiplePlans = false) {

    @Inject lateinit var onboardingTelemetry: OnboardingTelemetry

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding.buttonNotNow.isVisible = true
        binding.composeToolbar.isVisible = false
    }

    override fun onPaymentSuccess(newPlanName: String, upgradeFlowType: UpgradeFlowType) {
        super.onPaymentSuccess(newPlanName, upgradeFlowType)
        onboardingTelemetry.onOnboardingPaymentSuccess(newPlanName)
    }

    override fun initHighlightsFragment() {
        supportFragmentManager.commitNow {
            add(R.id.fragmentContent, UpgradeHighlightsOnboardingFragment::class.java, null)
        }
    }

    override fun onPlanSelected(planName: String) {
        DebugUtils.fail("Not supported")
    }

    override fun getTelemetryUpgradeSource(): UpgradeSource = UpgradeSource.ONBOARDING

    companion object {
        fun launch(context: Context) {
            context.startActivity(Intent(context, UpgradeOnboardingDialogActivity::class.java))
        }
    }
}

@Composable
private fun CloseButtonAndPlanSelectionToolbar(
    allPlans: List<PlanModel>,
    selectedPlan: PlanModel?,
    inProgress: Boolean,
    onClose: () -> Unit,
    onPlanSelected: (PlanModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        val closeButtonSizeWithPaddings = 56.dp
        Icon(
            painter = painterResource(CoreR.drawable.ic_proton_cross),
            contentDescription = stringResource(R.string.close),
            modifier = Modifier
                .padding(start = 8.dp)
                .clip(CircleShape)
                .clickable { onClose() }
                .padding(8.dp)
        )
        if (selectedPlan != null && allPlans.size > 1) {
            PlanSelector(
                allPlans,
                selectedPlan,
                enabled = !inProgress,
                onPlanSelected = onPlanSelected,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .layout { measurable, constraints ->
                        // Center the plan selector with respect to the whole row (take close button into
                        // account). If there's not enough space to center, then align to start and take as
                        // much available space as needed.
                        val softEndMargin = closeButtonSizeWithPaddings.toPx()
                        val placeable = measurable.measure(constraints)

                        val availableWidthWithEndMargin = constraints.maxWidth - softEndMargin
                        val x = when {
                            placeable.width <= availableWidthWithEndMargin ->
                                ((availableWidthWithEndMargin - placeable.width) / 2).roundToInt()

                            else -> 0
                        }
                        layout(placeable.width, placeable.height) {
                            placeable.placeRelative(x, 0)
                        }
                    }
            )
        }
    }
}

@Preview(widthDp = 450)
@Composable
private fun PreviewCloseButtonAndPlanSelectionToolbar() {
    LightAndDarkPreview(
        surfaceColor = { Color(0xFF3A51A6) }
    ) {
        val plans = listOf(
            PlanModel("VPN Plus", "plus", emptyList()),
            PlanModel("Proton Unlimited", "bundle", emptyList())
        )
        CloseButtonAndPlanSelectionToolbar(
            allPlans = plans,
            selectedPlan = plans.first(),
            inProgress = false,
            onClose = {},
            onPlanSelected = {}
        )
    }
}

@Preview(widthDp = 300)
@Composable
private fun PreviewNarrowCloseButtonAndPlanSelectionToolbar() {
    PreviewCloseButtonAndPlanSelectionToolbar()
}

@Preview(fontScale = 1.5f)
@Composable
private fun PreviewLargeFontCloseButtonAndPlanSelectionToolbar() {
    PreviewCloseButtonAndPlanSelectionToolbar()
}
