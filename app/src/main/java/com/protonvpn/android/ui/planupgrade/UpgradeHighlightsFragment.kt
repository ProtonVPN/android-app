/*
 * Copyright (c) 2023. Proton AG
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

import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.view.ViewOutlineProvider
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.view.children
import androidx.core.view.doOnLayout
import androidx.core.view.doOnNextLayout
import androidx.core.view.isNotEmpty
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.animation.ArgbEvaluatorCompat
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.theme.VpnTheme
import com.protonvpn.android.databinding.FragmentPlanHighlightsBinding
import com.protonvpn.android.databinding.FragmentUpgradeHighlightsBinding
import com.protonvpn.android.databinding.FragmentUpgradeHighlightsCarouselBinding
import com.protonvpn.android.databinding.ItemUpgradeFeatureBinding
import com.protonvpn.android.databinding.UpgradeCountryCustomImageBinding
import com.protonvpn.android.telemetry.UpgradeSource
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.utils.HtmlTools
import com.protonvpn.android.utils.ViewUtils.toPx
import com.protonvpn.android.utils.getSerializableCompat
import com.protonvpn.android.utils.getThemeColor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.presentation.utils.viewBinding
import kotlin.reflect.KClass
import me.proton.core.presentation.R as CoreR

// Convenience functions
private fun carouselItem(fragmentFactory: () -> Fragment, backgroundGradientOverride: Triple<Int, Int, Int>? = null) =
    UpgradeHighlightsCarouselFragment.CarouselItem(fragmentFactory, backgroundGradientOverride)

private fun vpnPlusCarouselFragments(hasCustomDns: Boolean) = buildList {
    add(carouselItem(::UpgradePlusCountriesHighlightsFragment))
    add(carouselItem(::UpgradeVpnAcceleratorHighlightsFragment))
    add(carouselItem(::UpgradeStreamingHighlightsFragment))
    add(carouselItem(::UpgradeNetShieldHighlightsFragment))
    add(carouselItem(::UpgradeSecureCoreHighlightsFragment))
    add(carouselItem(::UpgradeP2PHighlightsFragment))
    add(carouselItem(::UpgradeDevicesHighlightsFragment))
    add(carouselItem(::UpgradeTorHighlightsFragment))
    add(carouselItem(::UpgradeSplitTunnelingHighlightsFragment))
    add(carouselItem(::UpgradeProfilesHighlightsFragment))
    if (hasCustomDns) {
        // Note: when removing the Custom DNS feature flag simplify the UpgradeAdvancedCustomizationHighlightsFragment
        // class hierarchy.
        add(carouselItem(::UpgradeAdvancedCustomizationHighlightsFragment))
    } else {
        add(carouselItem(::UpgradeAdvancedCustomizationLegacyHighlightsFragment))
    }
}

private fun unlimitedCarouselFragments(hasCustomDns: Boolean) = listOf(
    carouselItem(::UpgradeUnlimitedAllAppsFragment, UnlimitedPlanBenefits.defaultGradient),
) + UnlimitedPlanBenefits.apps.map { app -> carouselItem({ UpgradeUnlimitedAppFragment(app) }, app.backgroundGradient) }

abstract class PlanHighlightsFragment : Fragment(R.layout.fragment_plan_highlights) {

    protected val binding by viewBinding(FragmentPlanHighlightsBinding::bind)
    protected val viewModel by viewModels<PlanHighlightsViewModel>()

    protected fun FragmentPlanHighlightsBinding.set(
        imageResource: Int?,
        title: String,
        message: CharSequence? = null,
        initFeatures: (LinearLayout.() -> Unit)? = null
    ) {
        imagePicture.isVisible = imageResource != null
        imageResource?.let { imagePicture.setImageResource(it) }

        textTitle.text = title
        textMessage.isVisible = message != null
        message?.let { textMessage.text = it }

        initFeatures?.invoke(layoutFeatureItems)
        if (layoutFeatureItems.isNotEmpty()) {
            layoutFeatureItems.isVisible = true
        }
    }

    protected fun ViewGroup.addFeature(
        @StringRes textRes: Int,
        @DrawableRes iconRes: Int,
        highlighted: Boolean = false
    ) {
        val htmlText = context.getString(textRes)
        addFeature(HtmlTools.fromHtml(htmlText), iconRes, highlighted)
    }

    protected fun ViewGroup.addFeature(text: CharSequence, @DrawableRes iconRes: Int, highlighted: Boolean = false) {
        val views = ItemUpgradeFeatureBinding.inflate(LayoutInflater.from(context), this, true)
        views.text.text = text
        views.icon.setImageResource(iconRes)
        if (highlighted) {
            val highlightColor = resources.getColor(R.color.protonVpnGreen, null)
            views.text.setTextColor(highlightColor)
            views.icon.setColorFilter(highlightColor)
        }
    }
}

private fun FragmentUpgradeHighlightsBinding.set(
    imageResource: Int?,
    title: String,
    message: CharSequence? = null,
) {
    imagePicture.isVisible = imageResource != null
    imageResource?.let { imagePicture.setImageResource(it) }

    textTitle.text = title
    textMessage.isVisible = message != null
    message?.let { textMessage.text = it }
}

abstract class FragmentWithUpgradeSource(
    @LayoutRes layoutId: Int,
    val upgradeSource: UpgradeSource
) : Fragment(layoutId)

abstract class UpgradeHighlightsFragmentWithSource(
    upgradeSource: UpgradeSource
) : FragmentWithUpgradeSource(R.layout.fragment_upgrade_highlights, upgradeSource) {

    protected val binding by viewBinding(FragmentUpgradeHighlightsBinding::bind)
    protected val viewModel by viewModels<PlanHighlightsViewModel>()
}

abstract class UpgradeHighlightsFragment : Fragment(R.layout.fragment_upgrade_highlights) {
    protected val binding by viewBinding(FragmentUpgradeHighlightsBinding::bind)
    protected val viewModel by viewModels<PlanHighlightsViewModel>()
}

@AndroidEntryPoint
abstract class UpgradeHighlightsCarouselFragment(
    private val carouselFragments: (hasCustomDns: Boolean) -> List<CarouselItem>,
) : Fragment(R.layout.fragment_upgrade_highlights_carousel) {

    class CarouselItem(
        val fragmentFactory: () -> Fragment,
        val backgroundGradientOverride: Triple<Int, Int, Int>? = null
    )

    private val viewModel: UpgradeHighlightsCarouselViewModel by viewModels(ownerProducer = { requireActivity() })
    private val binding by viewBinding(FragmentUpgradeHighlightsCarouselBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding.indicator) {
            tintIndicator(
                getThemeColor(CoreR.attr.proton_icon_accent),
                getThemeColor(CoreR.attr.proton_interaction_weak)
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val hasCustomDns = viewModel.hasCustomDns()
            val carousel = carouselFragments(hasCustomDns)
            val slideAdapter = SlideAdapter(this@UpgradeHighlightsCarouselFragment, carousel.map { it.fragmentFactory })
            with(binding.viewPager) {
                visibility = View.INVISIBLE
                // Keep all slides so their heights can be measured.
                offscreenPageLimit = slideAdapter.itemCount
                adapter = slideAdapter
                registerOnPageChangeCallback(PagerGradientUpdater(carousel, viewModel::setGradientOverride))

                // Update ViewPager's height to match the largest slide.
                doOnLayout {
                    val pagerChildren = (getChildAt(0) as RecyclerView).children
                    val maxMeasuredChildHeight = pagerChildren.maxOf {
                        it.measure(
                            View.MeasureSpec.makeMeasureSpec(it.width, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                        )
                        it.measuredHeight
                    }

                    val newHeight = maxMeasuredChildHeight + paddingTop + paddingBottom
                    if (newHeight != height) {
                        updateLayoutParams<LayoutParams> { this.height = newHeight }
                        doOnNextLayout {
                            visibility = View.VISIBLE
                        }
                    } else {
                        visibility = View.VISIBLE
                    }
                }
            }
            // The indicator doesn't implement saved state handling, therefore postpone its initialization till after
            // view pager's state is restored - otherwise they get out of sync.
            binding.indicator.doOnNextLayout { binding.indicator.setViewPager(binding.viewPager) }

            focusFragment(binding.viewPager, slideAdapter)
        }
    }

    private fun focusFragment(viewPager: ViewPager2, slideAdapter: SlideAdapter) {
        val focusedFragmentClass = focusedFragment(arguments)
        if (focusedFragmentClass != null) {
            val index = slideAdapter.indexOf(focusedFragmentClass)
            if (index >= 0) {
                viewPager.setCurrentItem(index, false)
            }
        }
    }

    private class SlideAdapter(
        fragment: Fragment,
        fragmentConstructors: List<() -> Fragment>,
    ) : FragmentStateAdapter(
        fragment.childFragmentManager,
        fragment.viewLifecycleOwner.lifecycle
    ) {
        // The ViewPager is configured to create all fragments anyway for proper sizing, therefore it's safe to
        // store the fragments.
        private val fragments = fragmentConstructors.map { it() }

        override fun getItemCount(): Int = fragments.size

        override fun createFragment(position: Int): Fragment = fragments[position]

        fun indexOf(fragmentClass: Class<out Fragment>) = fragments.indexOfFirst { fragmentClass.isInstance(it) }
    }

    private class PagerGradientUpdater(
        private val carouselFragments: List<CarouselItem>,
        private val onGradientChanged: (Triple<Int, Int, Int>?) -> Unit
    ) : ViewPager2.OnPageChangeCallback() {
        private val interpolator = ArgbEvaluatorCompat()

        override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
            if (positionOffset == 0f) {
                onGradientChanged(carouselFragments[position].backgroundGradientOverride)
            } else {
                val prev = carouselFragments[position].backgroundGradientOverride
                val next = carouselFragments[position + 1].backgroundGradientOverride
                if (prev != null && next != null) {
                    val gradient = Triple(
                        interpolator.evaluate(positionOffset, prev.first, next.first),
                        interpolator.evaluate(positionOffset, prev.second, next.second),
                        interpolator.evaluate(positionOffset, prev.third, next.third),
                    )
                    onGradientChanged(gradient)
                } else {
                    onGradientChanged(null)
                }
            }
        }
    }

    companion object {
        private const val EXTRA_FOCUSED_FRAGMENT_CLASS = "focusedFragmentClass"

        fun args(focusedFragmentClass: KClass<out Fragment>) = Bundle().apply {
            putSerializable(EXTRA_FOCUSED_FRAGMENT_CLASS, focusedFragmentClass.java)
        }

        fun focusedFragment(args: Bundle?) =
            args?.getSerializableCompat<Class<out Fragment>>(EXTRA_FOCUSED_FRAGMENT_CLASS)
    }
}

@AndroidEntryPoint
class UpgradeHighlightsOnboardingFragment : UpgradeHighlightsCarouselFragment(
    { hasCustomDns ->
        listOf(CarouselItem(::UpgradeVpnPlusHighlightsFragment)) + vpnPlusCarouselFragments(hasCustomDns)
    }
)

@AndroidEntryPoint
class UpgradeCarouselVpnPlusHighlightsFragment : UpgradeHighlightsCarouselFragment(::vpnPlusCarouselFragments)

@AndroidEntryPoint
class UpgradeCarouselUnlimitedHighlightsFragment : UpgradeHighlightsCarouselFragment(::unlimitedCarouselFragments)


@AndroidEntryPoint
class UpgradeVpnPlusHighlightsFragment : UpgradeHighlightsFragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.set(
            imageResource = R.drawable.upgrade_vpn_plus,
            title = getString(R.string.upgrade_plus_title),
            message = getString(R.string.upgrade_plus_message)
        )
    }
}

@AndroidEntryPoint
class UpgradeNetShieldHighlightsFragment : UpgradeHighlightsFragmentWithSource(UpgradeSource.NETSHIELD) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.set(
            imageResource = R.drawable.upgrade_netshield,
            title = getString(R.string.upgrade_netshield_title),
            message = HtmlTools.fromHtml(getString(R.string.upgrade_netshield_message)),
        )
    }
}

@AndroidEntryPoint
class UpgradeSecureCoreHighlightsFragment : UpgradeHighlightsFragmentWithSource(UpgradeSource.SECURE_CORE) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.set(
            imageResource = R.drawable.upgrade_secure_core,
            title = getString(R.string.upgrade_secure_core_title),
            message = HtmlTools.fromHtml(getString(R.string.upgrade_secure_core_message))
        )
    }
}

@AndroidEntryPoint
class UpgradeVpnAcceleratorHighlightsFragment : UpgradeHighlightsFragmentWithSource(UpgradeSource.VPN_ACCELERATOR) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.set(
            imageResource = R.drawable.upgrade_speed,
            title = getString(R.string.upgrade_vpn_accelerator_title),
            message = getString(R.string.upgrade_vpn_accelerator_message, Constants.SERVER_SPEED_UP_TO_GBPS)
        )
    }
}

@AndroidEntryPoint
class UpgradeStreamingHighlightsFragment : UpgradeHighlightsFragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.set(
            imageResource = R.drawable.upgrade_streaming,
            title = getString(R.string.upgrade_streaming_title),
            message = getString(R.string.upgrade_streaming_text)
        )
    }
}

@AndroidEntryPoint
class UpgradeTorHighlightsFragment : UpgradeHighlightsFragmentWithSource(UpgradeSource.TOR) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.set(
            imageResource = R.drawable.upgrade_tor,
            title = getString(R.string.upgrade_tor_title),
            message = getString(R.string.upgrade_tor_text)
        )
    }
}

@AndroidEntryPoint
class UpgradeDevicesHighlightsFragment : UpgradeHighlightsFragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val devices = Constants.MAX_CONNECTIONS_IN_PLUS_PLAN
        val title = resources.getQuantityString(R.plurals.upgrade_devices_title, devices, devices)
        binding.set(
            imageResource = R.drawable.upgrade_devices,
            title = title,
            message = getString(R.string.upgrade_devices_text)
        )
    }
}

@AndroidEntryPoint
class UpgradeP2PHighlightsFragment : UpgradeHighlightsFragmentWithSource(UpgradeSource.P2P) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.set(
            imageResource = R.drawable.upgrade_p2p,
            title = getString(R.string.upgrade_p2p_title),
            message = getString(R.string.upgrade_p2p_text)
        )
    }
}

// Note: when removing the Custom DNS feature flag this class will become a concrete implementation without subclasses.
@AndroidEntryPoint
open class UpgradeAdvancedCustomizationHighlightsFragment : UpgradeHighlightsFragmentWithSource(
    UpgradeSource.ADVANCED_CUSTOMIZATION
) {
    protected open val messageRes: Int = R.string.upgrade_customization_message

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.set(
            imageResource = R.drawable.upgrade_customization,
            title = getString(R.string.upgrade_customization_title),
            message = HtmlTools.fromHtml(getString(messageRes))
        )
    }
}

@AndroidEntryPoint
class UpgradeAdvancedCustomizationLegacyHighlightsFragment : UpgradeAdvancedCustomizationHighlightsFragment() {
    override val messageRes: Int = R.string.upgrade_lan_access_message
}

@AndroidEntryPoint
class UpgradeSplitTunnelingHighlightsFragment : UpgradeHighlightsFragmentWithSource(UpgradeSource.SPLIT_TUNNELING) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.set(
            imageResource = R.drawable.upgrade_split_tunneling,
            title = getString(R.string.upgrade_split_tunneling_title),
            message = HtmlTools.fromHtml(getString(R.string.upgrade_split_tunneling_message)),
        )
    }
}

@AndroidEntryPoint
class UpgradeProfilesHighlightsFragment : UpgradeHighlightsFragmentWithSource(UpgradeSource.PROFILES) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.set(
            imageResource = R.drawable.upgrade_profiles,
            title = getString(R.string.upgrade_profiles_title),
            message = HtmlTools.fromHtml(getString(R.string.upgrade_profiles_text)),
        )
    }
}

@AndroidEntryPoint
class UpgradeCountryHighlightsFragment : UpgradeHighlightsFragmentWithSource(UpgradeSource.COUNTRIES) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val countryCode = requireArguments().getString(ARG_COUNTRY)

        binding.set(
            imageResource = null,
            title = getString(R.string.upgrade_country_title),
            message = HtmlTools.fromHtml(getString(R.string.upgrade_country_message))
        )

        val customImageBinding = UpgradeCountryCustomImageBinding.inflate(
            LayoutInflater.from(requireContext()),
            binding.customViewContainer,
            true
        )
        val flag = CountryTools.getFlagResource(requireContext(), countryCode)
        binding.customViewContainer.isVisible = true

        customImageBinding.upgradeFlag.setImageResource(flag)
        customImageBinding.upgradeFlag.clipToOutline = true
        customImageBinding.upgradeFlag.outlineProvider = object: ViewOutlineProvider() {
            override fun getOutline(view: View?, outline: android.graphics.Outline?) {
                val dyDp = (FLAG_WIDTH_DP - FLAG_HEIGHT_DP) / 2
                outline?.setRoundRect(
                    Rect(
                        0, dyDp.toPx(),
                        FLAG_WIDTH_DP.toPx(), (FLAG_WIDTH_DP - dyDp).toPx()
                    ),
                    6.toPx().toFloat()
                )
            }
        }
    }

    companion object {
        const val ARG_COUNTRY = "country"
        private const val FLAG_WIDTH_DP = 48
        private const val FLAG_HEIGHT_DP = 32

        fun args(countryCode: String) = Bundle().apply { putString(ARG_COUNTRY, countryCode) }
    }
}

@AndroidEntryPoint
class UpgradePlusCountriesHighlightsFragment : UpgradeHighlightsFragmentWithSource(UpgradeSource.COUNTRIES) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.set(
            imageResource = R.drawable.upgrade_worldwide_coverage,
            title = getString(R.string.upgrade_all_countries_title),
            message = createMessage()
        )
    }

    private fun createMessage(): CharSequence {
        val serverCount = viewModel.allServersCount()
        val countryCount = viewModel.countriesCount()

        val roundedServerCount = serverCount / 100 * 100
        val servers = resources.getQuantityString(
            R.plurals.upgrade_plus_servers_new,
            roundedServerCount,
            roundedServerCount
        )
        val countries = resources.getQuantityString(
            R.plurals.upgrade_plus_countries,
            countryCount,
            countryCount
        )
        return HtmlTools.fromHtml(getString(R.string.upgrade_plus_countries_title_new, servers, countries))
    }
}

abstract class UpgradeComposeFragment : Fragment(R.layout.fragment_upgrade_highlights_compose) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (view as ComposeView).setContent {
            VpnTheme {
                Content(modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp))
            }
        }
    }

    @Composable
    protected abstract fun Content(modifier: Modifier)
}

@AndroidEntryPoint
class UpgradeUnlimitedAllAppsFragment : UpgradeComposeFragment() {

    @Composable
    override fun Content(modifier: Modifier) {
        Column(
            modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.upgrade_unlimited_all_apps),
                contentDescription = null,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            Text(
                text = stringResource(R.string.upgrade_unlimited_all_apps_title),
                style = ProtonTheme.typography.headline,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Text(
                text = stringResource(R.string.upgrade_unlimited_all_apps_description),
                style = ProtonTheme.typography.body1Regular,
                color = ProtonTheme.colors.textWeak,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@AndroidEntryPoint
class UpgradeUnlimitedAppFragment(private val app: AppBenefits) : UpgradeComposeFragment() {

    @Composable
    override fun Content(modifier: Modifier) {
        Column(
            modifier.padding(horizontal = 16.dp)
        ) {
            Image(
                painter = painterResource(id = app.logo),
                contentDescription = stringResource(app.logoContentDescription),
                modifier = Modifier
                    .padding(bottom = 24.dp)
                    .height(36.dp)
            )
            val itemModifier = Modifier.padding(bottom = 8.dp)
            app.mainBenefits.forEach { benefitText ->
                Row(itemModifier) {
                    Icon(
                        painterResource(CoreR.drawable.ic_proton_checkmark),
                        contentDescription = null,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(16.dp)
                    )
                    Text(planStringResource(benefitText))
                }
            }
            var showMoreInfoBottomSheet by rememberSaveable { mutableStateOf(false)}
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(start = 20.dp)
                    .clickable { showMoreInfoBottomSheet = true }
            ) {
                Text(
                    stringResource(R.string.upgrade_unlimited_more),
                    textDecoration = TextDecoration.Underline,
                    color = ProtonTheme.colors.textWeak
                )
                Icon(
                    painterResource(CoreR.drawable.ic_proton_info_circle_filled),
                    contentDescription = null,
                    tint = ProtonTheme.colors.iconWeak,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(16.dp)
                )
            }

            if (showMoreInfoBottomSheet) {
                UnlimitedPlanBenefitsBottomSheet(onDismissRequest = { showMoreInfoBottomSheet = false }, app)
            }
        }
    }
}
