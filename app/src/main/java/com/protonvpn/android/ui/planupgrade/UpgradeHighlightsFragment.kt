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
import androidx.core.view.children
import androidx.core.view.doOnNextLayout
import androidx.core.view.isNotEmpty
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.protonvpn.android.R
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
import me.proton.core.presentation.utils.viewBinding
import kotlin.reflect.KClass
import me.proton.core.presentation.R as CoreR


private val FeatureCarouselFragments = listOf(
    ::UpgradePlusCountriesHighlightsFragment,
    ::UpgradeVpnAcceleratorHighlightsFragment,
    ::UpgradeStreamingHighlightsFragment,
    ::UpgradeNetShieldHighlightsFragment,
    ::UpgradeSecureCoreHighlightsFragment,
    ::UpgradeP2PHighlightsFragment,
    ::UpgradeDevicesHighlightsFragment,
    ::UpgradeTorHighlightsFragment,
    ::UpgradeSplitTunnelingHighlightsFragment,
    ::UpgradeAllowLanHighlightsFragment,
)

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
    private val carouselFragments: List<() -> Fragment>,
) : Fragment(R.layout.fragment_upgrade_highlights_carousel) {

    private val binding by viewBinding(FragmentUpgradeHighlightsCarouselBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding.indicator) {
            tintIndicator(
                getThemeColor(CoreR.attr.proton_icon_accent),
                getThemeColor(CoreR.attr.proton_interaction_weak)
            )
        }

        val slideAdapter = SlideAdapter(this, carouselFragments)
        with(binding.viewPager) {
            visibility = View.INVISIBLE
            // Keep all slides so their heights can be measured.
            offscreenPageLimit = slideAdapter.itemCount
            adapter = slideAdapter

            // Update ViewPager's height to match the largest slide.
            viewTreeObserver.addOnGlobalLayoutListener {
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
        binding.indicator.setViewPager(binding.viewPager)

        binding.viewPager.doOnNextLayout {
            focusFragment(binding.viewPager)
        }
    }

    private fun focusFragment(viewPager: ViewPager2) {
        val focusedFragmentClass =
            arguments?.getSerializableCompat<Class<out Fragment>>(EXTRA_FOCUSED_FRAGMENT_CLASS)
        if (focusedFragmentClass != null) {
            val index = childFragmentManager.fragments.indexOfFirst { fragment ->
                focusedFragmentClass.isInstance(fragment)
            }
            if (index >= 0) {
                viewPager.setCurrentItem(index, false)
            }
        }
    }

    private class SlideAdapter(
        fragment: Fragment,
        private val fragmentConstructors: List<() -> Fragment>,
    ) : FragmentStateAdapter(
        fragment.childFragmentManager,
        fragment.viewLifecycleOwner.lifecycle
    ) {
        override fun getItemCount(): Int = fragmentConstructors.size

        override fun createFragment(position: Int): Fragment = fragmentConstructors[position]()
    }

    companion object {
        const val EXTRA_FOCUSED_FRAGMENT_CLASS = "focusedFragmentClass"

        fun args(focusedFragmentClass: KClass<out Fragment>) = Bundle().apply {
            putSerializable(EXTRA_FOCUSED_FRAGMENT_CLASS, focusedFragmentClass.java)
        }
    }
}

@AndroidEntryPoint
class UpgradeHighlightsOnboardingFragment : UpgradeHighlightsCarouselFragment(
    listOf(::UpgradeVpnPlusHighlightsFragment) + FeatureCarouselFragments
)

@AndroidEntryPoint
class UpgradeHighlightsHomeCardsFragment : UpgradeHighlightsCarouselFragment(FeatureCarouselFragments)

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

@AndroidEntryPoint
class UpgradeAllowLanHighlightsFragment : UpgradeHighlightsFragmentWithSource(UpgradeSource.ALLOW_LAN) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.set(
            imageResource = R.drawable.upgrade_customization,
            title = getString(R.string.upgrade_customization_title),
            message = HtmlTools.fromHtml(getString(R.string.upgrade_lan_access_message))
        )
    }
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

@AndroidEntryPoint
class UpgradeModerateNatHighlightsFragment : UpgradeHighlightsFragmentWithSource(UpgradeSource.MODERATE_NAT) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.set(
            imageResource = R.drawable.upgrade_moderate_nat,
            title = getString(R.string.upgrade_moderate_nat_title2),
            message = HtmlTools.fromHtml(getString(R.string.upgrade_moderate_nat_message2)),
        )
    }
}
