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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Observer
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseActivityV2
import com.protonvpn.android.databinding.ActivityUpgradeDialogBinding
import com.protonvpn.android.databinding.ItemUpgradeFeatureBinding
import com.protonvpn.android.utils.AndroidUtils.setContentViewBinding
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.openProtonUrl
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

abstract class UpgradeDialogActivity : BaseActivityV2() {

    val viewModel by viewModels<UpgradeDialogViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = setContentViewBinding(ActivityUpgradeDialogBinding::inflate)
        viewModel.setupOrchestrators(this)
        viewModel.upgradeResult.asLiveData().observe(this, Observer { result ->
            if (result != null) {
                if (result.billingResult.subscriptionCreated)
                    startActivity(CongratsPlanActivity.create(this))
                finish()
            }
        })

        binding.buttonOther.setOnClickListener { finish() }
        binding.buttonUpgrade.setOnClickListener {
            lifecycleScope.launch {
                viewModel.planUpgrade()
            }
        }

        setViews(binding)
    }

    protected abstract fun setViews(binding: ActivityUpgradeDialogBinding)
}

// Directly navigates to plan upgrade workflow
@AndroidEntryPoint
class EmptyUpgradeDialogActivity : AppCompatActivity() {

    val viewModel by viewModels<UpgradeDialogViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.setupOrchestrators(this)
        viewModel.upgradeResult.asLiveData().observe(this, Observer { result ->
            if (result != null && result.billingResult.subscriptionCreated)
                startActivity(CongratsPlanActivity.create(this))
            finish()
        })
        lifecycleScope.launch {
            viewModel.planUpgrade()
        }
    }
}

@AndroidEntryPoint
class UpgradeNetShieldDialogActivity : UpgradeDialogActivity() {

    override fun setViews(binding: ActivityUpgradeDialogBinding) = with(binding) {
        imagePicture.setImageResource(R.drawable.upgrade_netshield)
        textTitle.setText(R.string.upgrade_netshield_title)
        textMessage.setText(R.string.upgrade_netshield_message)
    }
}

@AndroidEntryPoint
class UpgradeSecureCoreDialogActivity : UpgradeDialogActivity() {

    override fun setViews(binding: ActivityUpgradeDialogBinding) = with(binding) {
        imagePicture.setImageResource(R.drawable.upgrade_secure_core)
        textTitle.setText(R.string.upgrade_secure_core_title)
        textMessage.setText(R.string.upgrade_secure_core_message)
    }
}

@AndroidEntryPoint
open class UpgradePlusCountriesDialogActivity : UpgradeDialogActivity() {

    override fun setViews(binding: ActivityUpgradeDialogBinding) = with(binding) {
        imagePicture.setImageResource(R.drawable.upgrade_plus_countries)
        textTitle.text = createTitle()
        // No margin between the image and title, the image fades out at the bottom.
        textTitle.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin = 0 }
        textMessage.setText(R.string.upgrade_plus_countries_message)

        with(layoutFeatureItems) {
            val manyDevices = resources.getQuantityString(
                R.plurals.upgrade_plus_countries_many_devices,
                Constants.MAX_CONNECTIONS_IN_PLUS_PLAN,
                Constants.MAX_CONNECTIONS_IN_PLUS_PLAN
            )
            addFeature(R.string.upgrade_plus_countries_streaming, R.drawable.ic_play)
            addFeature(manyDevices, R.drawable.ic_mobile_add)
            addFeature(R.string.upgrade_plus_countries_netshield, R.drawable.ic_shield)
            addFeature(R.string.upgrade_plus_countries_speeds, R.drawable.ic_rocket)
            isVisible = true
        }
    }

    private fun createTitle(): String {
        val serverCount = serverManager.allServerCount
        val countryCount = serverManager.getVpnCountries().size

        val roundedServerCount = serverCount / 100 * 100
        val servers = resources.getQuantityString(
            R.plurals.upgrade_plus_servers,
            roundedServerCount,
            roundedServerCount
        )
        val countries = resources.getQuantityString(
            R.plurals.upgrade_plus_countries,
            countryCount,
            countryCount
        )
        return getString(R.string.upgrade_plus_countries_title, servers, countries)
    }

    private fun ViewGroup.addFeature(@StringRes textRes: Int, @DrawableRes iconRes: Int) {
        addFeature(getString(textRes), iconRes)
    }

    private fun ViewGroup.addFeature(text: String, @DrawableRes iconRes: Int) {
        val views = ItemUpgradeFeatureBinding.inflate(LayoutInflater.from(context), this, true)
        views.text.text = text
        views.text.setCompoundDrawablesRelativeWithIntrinsicBounds(iconRes, 0, 0, 0)
    }
}

@AndroidEntryPoint
class UpgradePlusOnboardingDialogActivity : UpgradePlusCountriesDialogActivity() {
    override fun setViews(binding: ActivityUpgradeDialogBinding) {
        super.setViews(binding)
        with(binding) {
            buttonOther.setText(R.string.upgrade_use_limited_free_button)
        }
    }
}

@AndroidEntryPoint
class UpgradeSafeModeDialogActivity : UpgradeDialogActivity() {

    override fun setViews(binding: ActivityUpgradeDialogBinding) {
        with(binding) {
            initToolbarWithUpEnabled(toolbar)
            supportActionBar?.setDisplayShowTitleEnabled(false)
            toolbar.isVisible = true

            imagePicture.setImageResource(R.drawable.upgrade_safemode)
            textTitle.setText(R.string.upgrade_safe_mode_title)
            textMessage.setText(R.string.upgrade_safe_mode_message)

            buttonOther.setText(R.string.upgrade_learn_more)
            buttonOther.setOnClickListener { openProtonUrl(Constants.SAFE_MODE_INFO_URL) }
        }
    }

}
