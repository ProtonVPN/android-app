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
import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseActivityV2
import com.protonvpn.android.databinding.ActivityUpsellDialogBinding
import com.protonvpn.android.databinding.UpgradeCountryCustomImageBinding
import com.protonvpn.android.telemetry.UpgradeSource
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.utils.HtmlTools
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.ViewUtils.toPx
import com.protonvpn.android.utils.ViewUtils.viewBinding
import com.protonvpn.android.utils.openProtonUrl
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

abstract class UpgradeDialogActivity(private val upgradeSource: UpgradeSource) : BaseActivityV2() {

    protected val viewModel by viewModels<UpgradeDialogViewModel>()
    protected val binding by viewBinding(ActivityUpsellDialogBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        viewModel.reportUpgradeFlowStart(upgradeSource)
        viewModel.setupOrchestrators(this)
        viewModel.state.asLiveData().observe(this) { state ->
            if (state == UpgradeDialogViewModel.State.Success) {
                startActivity(CongratsPlanActivity.create(this))
                setResult(Activity.RESULT_OK)
                finish()
            }
        }
    }
}

// Directly navigates to plan upgrade workflow
@AndroidEntryPoint
class EmptyUpgradeDialogActivity : AppCompatActivity() {

    val viewModel by viewModels<UpgradeDialogViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.setupOrchestrators(this)
        viewModel.state.asLiveData().observe(this) { state ->
            if (state == UpgradeDialogViewModel.State.Success) {
                startActivity(CongratsPlanActivity.create(this))
                setResult(Activity.RESULT_OK)
            }
            if (state != UpgradeDialogViewModel.State.Init)
                finish()
        }
        lifecycleScope.launch {
            viewModel.planUpgrade()
        }
    }
}

@AndroidEntryPoint
class UpgradeNetShieldDialogActivity : UpgradeDialogActivity(UpgradeSource.NETSHIELD) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding.initUpgradeBinding(
            this,
            viewModel,
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
class UpgradeSecureCoreDialogActivity : UpgradeDialogActivity(UpgradeSource.SECURE_CORE) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding.initUpgradeBinding(
            this,
            viewModel,
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
class UpgradeProfilesDialogActivity : UpgradeDialogActivity(UpgradeSource.PROFILES) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding.initUpgradeBinding(
            this,
            viewModel,
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
class UpgradeVpnAcceleratorDialogActivity : UpgradeDialogActivity(UpgradeSource.VPN_ACCELERATOR) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding.initUpgradeBinding(
            this,
            viewModel,
            imageResource = R.drawable.upgrade_vpn_accelerator,
            title = getString(R.string.upgrade_vpn_accelerator_title)
        ) {
            addFeature(R.string.upgrade_vpn_accelerator_feature_speed, R.drawable.ic_proton_bolt)
            addFeature(R.string.upgrade_vpn_accelerator_feature_distant_servers, R.drawable.ic_proton_chart_line)
            addFeature(R.string.upgrade_vpn_accelerator_feature_less_crowded, R.drawable.ic_proton_servers)
        }
    }
}

@AndroidEntryPoint
abstract class UpgradeCustomizationDialogActivity(upgradeSource: UpgradeSource) : UpgradeDialogActivity(upgradeSource) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding.initUpgradeBinding(
            this,
            viewModel,
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
class UpgradeAllowLanDialogActivity : UpgradeCustomizationDialogActivity(UpgradeSource.ALLOW_LAN)

@AndroidEntryPoint
class UpgradeSplitTunnelingDialogActivity : UpgradeDialogActivity(UpgradeSource.SPLIT_TUNNELING) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding.initUpgradeBinding(
            this,
            viewModel,
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
class UpgradeCountryDialogActivity : UpgradeDialogActivity(UpgradeSource.COUNTRIES) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val countryCode = intent.getStringExtra(EXTRA_COUNTRY)
        val countriesCount = viewModel.countriesCount()

        binding.initUpgradeBinding(
            this,
            viewModel,
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
            LayoutInflater.from(this),
            binding.customViewContainer,true
        )
        val flag = CountryTools.getFlagResource(this, countryCode)
        binding.customViewContainer.isVisible = true

        customImageBinding.upgradeFlag.setImageResource(flag)
        customImageBinding.upgradeFlag.clipToOutline = true
        customImageBinding.upgradeFlag.outlineProvider = object: ViewOutlineProvider() {
            override fun getOutline(view: View?, outline: android.graphics.Outline?) {
                val dyDp = (FLAG_WIDTH_DP - FLAG_HEIGHT_DP) / 2
                outline?.setRoundRect(Rect(
                    0, dyDp.toPx(),
                    FLAG_WIDTH_DP.toPx(), (FLAG_WIDTH_DP - dyDp).toPx()
                ), 6.toPx().toFloat())
            }
        }
    }

    companion object {
        private const val EXTRA_COUNTRY = "COUNTRY"
        private const val FLAG_WIDTH_DP = 48
        private const val FLAG_HEIGHT_DP = 32

        @JvmStatic
        fun createIntent(context: Context, countryCode: String) =
            Intent(context, UpgradeCountryDialogActivity::class.java).apply {
                putExtra(EXTRA_COUNTRY, countryCode)
            }
    }
}

@AndroidEntryPoint
open class UpgradePlusCountriesDialogActivity : UpgradeDialogActivity(UpgradeSource.COUNTRIES) {

    @Inject lateinit var serverManager: ServerManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding.initUpgradeBinding(
            this,
            viewModel,
            imageResource = R.drawable.upgrade_plus_countries,
            title = createTitle(),
            message = getString(R.string.upgrade_plus_subtitle)
        ) {
            val manyDevices = resources.getQuantityString(
                R.plurals.upgrade_plus_countries_many_devices,
                Constants.MAX_CONNECTIONS_IN_PLUS_PLAN,
                Constants.MAX_CONNECTIONS_IN_PLUS_PLAN
            )
            addFeature(R.string.upgrade_plus_countries_streaming, R.drawable.ic_proton_play)
            addFeature(manyDevices, R.drawable.ic_proton_power_off)
            addFeature(R.string.upgrade_plus_countries_netshield, R.drawable.ic_proton_shield)
            addFeature(R.string.upgrade_plus_countries_speeds, R.drawable.ic_proton_rocket)
        }
        // No margin between the image and title, the image fades out at the bottom.
        binding.textTitle.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin = 0 }
        binding.upgradeCountriesMoreCaption.isVisible = true
    }

    private fun createTitle(): String {
        val serverCount = serverManager.allServerCount
        val countryCount = serverManager.getVpnCountries().size

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
class UpgradePlusOnboardingDialogActivity : UpgradePlusCountriesDialogActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        with(binding) {
            buttonOther.setText(R.string.upgrade_use_limited_free_button)
        }
    }
}

@AndroidEntryPoint
class UpgradeSafeModeDialogActivity : UpgradeDialogActivity(UpgradeSource.SAFE_MODE) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding.initUpgradeBinding(
            this,
            viewModel,
            imageResource = R.drawable.upgrade_non_standard_ports,
            title = getString(R.string.upgrade_safe_mode_title),
            message = getString(R.string.upgrade_safe_mode_message),
            otherButtonLabel = R.string.upgrade_learn_more,
            otherButtonAction = { openProtonUrl(Constants.SAFE_MODE_INFO_URL) }
        )
    }
}

@AndroidEntryPoint
class UpgradeModerateNatDialogActivity : UpgradeDialogActivity(UpgradeSource.MODERATE_NAT) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding.initUpgradeBinding(
            this,
            viewModel,
            imageResource = R.drawable.upgrade_moderate_nat,
            title = getString(R.string.upgrade_moderate_nat_title2),
            message = HtmlTools.fromHtml(getString(R.string.upgrade_moderate_nat_message2)),
        ) {
            addFeature(R.string.upgrade_moderate_nat_feature_gaming, R.drawable.ic_proton_map_pin)
            addFeature(R.string.upgrade_moderate_nat_feature_optimize, R.drawable.ic_proton_checkmark_circle)
        }
    }
}
