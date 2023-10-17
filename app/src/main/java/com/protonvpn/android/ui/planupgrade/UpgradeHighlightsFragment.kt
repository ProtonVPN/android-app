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
import android.view.ViewOutlineProvider
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.isNotEmpty
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.protonvpn.android.R
import com.protonvpn.android.databinding.FragmentPlanHighlightsBinding
import com.protonvpn.android.databinding.ItemUpgradeFeatureBinding
import com.protonvpn.android.databinding.UpgradeCountryCustomImageBinding
import com.protonvpn.android.telemetry.UpgradeSource
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.utils.HtmlTools
import com.protonvpn.android.utils.ViewUtils.toPx
import com.zhuinden.fragmentviewbindingdelegatekt.viewBinding
import dagger.hilt.android.AndroidEntryPoint

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

abstract class UpgradeHighlightsFragment(val upgradeSource: UpgradeSource) : PlanHighlightsFragment()

@AndroidEntryPoint
class UpgradeNetShieldHighlightsFragment : UpgradeHighlightsFragment(UpgradeSource.NETSHIELD) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.set(
            imageResource = R.drawable.upgrade_netshield,
            title = getString(R.string.upgrade_netshield_title_new),
            message = getString(R.string.upgrade_plus_subtitle),
        ) {
            addFeature(R.string.upgrade_netshield_block_ads, R.drawable.ic_proton_circle_slash)
            addFeature(R.string.upgrade_netshield_block_malware, R.drawable.ic_proton_shield)
            addFeature(R.string.upgrade_netshield_speed, R.drawable.ic_proton_rocket)
        }
    }
}

@AndroidEntryPoint
class UpgradeSecureCoreHighlightsFragment : UpgradeHighlightsFragment(UpgradeSource.SECURE_CORE) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.set(
            imageResource = R.drawable.upgrade_secure_core,
            title = getString(R.string.upgrade_secure_core_title_new),
            message = getString(R.string.upgrade_plus_subtitle)
        ) {
            addFeature(R.string.upgrade_secure_core_countries, R.drawable.ic_proton_servers)
            addFeature(R.string.upgrade_secure_core_encryption, R.drawable.ic_proton_lock)
            addFeature(R.string.upgrade_secure_core_protect, R.drawable.ic_proton_alias)
        }
    }
}

@AndroidEntryPoint
class UpgradeProfilesHighlightsFragment : UpgradeHighlightsFragment(UpgradeSource.PROFILES) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.set(
            imageResource = R.drawable.upgrade_profiles,
            title = getString(R.string.upgrade_profiles_title),
            message = getString(R.string.upgrade_profiles_text)
        ) {
            addFeature(R.string.upgrade_profiles_feature_save, R.drawable.ic_proton_globe)
            addFeature(R.string.upgrade_profiles_feature_customize, R.drawable.ic_proton_rocket)
        }
    }
}

@AndroidEntryPoint
class UpgradeVpnAcceleratorHighlightsFragment : UpgradeHighlightsFragment(UpgradeSource.VPN_ACCELERATOR) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.set(
            imageResource = R.drawable.upgrade_vpn_accelerator,
            title = getString(R.string.upgrade_vpn_accelerator_title)
        ) {
            addFeature(R.string.upgrade_vpn_accelerator_feature_speed, R.drawable.ic_proton_bolt)
            addFeature(R.string.upgrade_vpn_accelerator_feature_distant_servers, R.drawable.ic_proton_chart_line)
            addFeature(R.string.upgrade_vpn_accelerator_feature_less_crowded, R.drawable.ic_proton_servers)
        }
    }
}

abstract class UpgradeCustomizationHighlightsFragment(
    upgradeSource: UpgradeSource
) : UpgradeHighlightsFragment(upgradeSource) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.set(
            imageResource = R.drawable.upgrade_customization,
            title = getString(R.string.upgrade_customization_title)
        ) {
            addFeature(R.string.upgrade_customization_lan, R.drawable.ic_proton_printer)
            addFeature(R.string.upgrade_customization_feature_profiles, R.drawable.ic_proton_power_off)
            addFeature(R.string.upgrade_customization_feature_quick_connect_profile, R.drawable.ic_proton_bolt)
        }
    }
}

@AndroidEntryPoint
class UpgradeAllowLanHighlightsFragment : UpgradeCustomizationHighlightsFragment(UpgradeSource.ALLOW_LAN)

@AndroidEntryPoint
class UpgradeSplitTunnelingHighlightsFragment : UpgradeHighlightsFragment(UpgradeSource.SPLIT_TUNNELING) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.set(
            imageResource = R.drawable.upgrade_split_tunneling,
            title = getString(R.string.upgrade_split_tunneling_title),
            message = HtmlTools.fromHtml(getString(R.string.upgrade_split_tunneling_message)),
        ) {
            addFeature(R.string.upgrade_split_tunneling_two_locations, R.drawable.ic_proton_map_pin)
            addFeature(R.string.upgrade_split_tunneling_feature_allow_exceptions, R.drawable.ic_proton_checkmark_circle)
        }
    }
}

@AndroidEntryPoint
class UpgradeCountryHighlightsFragment : UpgradeHighlightsFragment(UpgradeSource.COUNTRIES) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val countryCode = requireArguments().getString(ARG_COUNTRY)
        val countriesCount = viewModel.countriesCount()

        binding.set(
            imageResource = null,
            title = getString(R.string.upgrade_country_title),
            message = getString(R.string.upgrade_country_message)
        ) {
            addFeature(resources.getQuantityString(R.plurals.upgrade_country_feature_count, countriesCount, countriesCount), R.drawable.ic_proton_globe)
            addFeature(getString(R.string.upgrade_country_feature_speed, 10), R.drawable.ic_proton_rocket)
            addFeature(R.string.upgrade_country_feature_stream, R.drawable.ic_proton_play)
            addFeature(resources.getQuantityString(R.plurals.upgrade_country_feature_protect, 10, 10), R.drawable.ic_proton_locks)
            addFeature(R.string.upgrade_country_feature_money_back,
                R.drawable.ic_proton_shield_filled, highlighted = true)
        }

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
class UpgradePlusCountriesHighlightsFragment : UpgradeHighlightsFragment(UpgradeSource.COUNTRIES) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.set(
            imageResource = R.drawable.upgrade_plus_countries,
            title = createTitle(),
            message = getString(R.string.upgrade_plus_subtitle)
        ) {
            addFeature(R.string.upgrade_plus_countries_choose_location, R.drawable.ic_proton_globe)
            addFeature(R.string.upgrade_plus_countries_even_higher_speed, R.drawable.ic_proton_rocket)
            addFeature(R.string.upgrade_plus_countries_access_content, R.drawable.ic_proton_lock_open)
            addFeature(R.string.upgrade_plus_countries_stream, R.drawable.ic_proton_play)
        }
        // No margin between the image and title, the image fades out at the bottom.
        binding.textTitle.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin = 0 }
    }

    private fun createTitle(): String {
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
        return getString(R.string.upgrade_plus_countries_title_new, servers, countries)
    }
}

@AndroidEntryPoint
class UpgradeSafeModeHighlightsFragment : UpgradeHighlightsFragment(UpgradeSource.SAFE_MODE) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.set(
            imageResource = R.drawable.upgrade_non_standard_ports,
            title = getString(R.string.upgrade_safe_mode_title),
            message = getString(R.string.upgrade_safe_mode_message),
        )
    }
}

@AndroidEntryPoint
class UpgradeModerateNatHighlightsFragment : UpgradeHighlightsFragment(UpgradeSource.MODERATE_NAT) {


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.set(
            imageResource = R.drawable.upgrade_moderate_nat,
            title = getString(R.string.upgrade_moderate_nat_title2),
            message = HtmlTools.fromHtml(getString(R.string.upgrade_moderate_nat_message2)),
        ) {
            addFeature(R.string.upgrade_moderate_nat_feature_gaming, R.drawable.ic_proton_map_pin)
            addFeature(R.string.upgrade_moderate_nat_feature_optimize, R.drawable.ic_proton_checkmark_circle)
        }
    }
}
