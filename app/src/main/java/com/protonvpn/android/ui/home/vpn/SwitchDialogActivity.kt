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
package com.protonvpn.android.ui.home.vpn

import android.os.Bundle
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.snackbar.Snackbar
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseActivityV2
import com.protonvpn.android.components.ContentLayout
import com.protonvpn.android.components.NotificationHelper
import com.protonvpn.android.databinding.ActivitySwitchDialogBinding
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.utils.Log
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.vpn.SwitchServerReason
import com.protonvpn.android.vpn.VpnFallbackResult
import javax.inject.Inject

@ContentLayout(R.layout.activity_switch_dialog)
class SwitchDialogActivity : BaseActivityV2<ActivitySwitchDialogBinding, ViewModel>() {

    @Inject lateinit var serverManager: ServerManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initUI()
    }

    private fun initUI() = with(binding) {
        val reconnectionNotification =
            intent.getSerializableExtra(EXTRA_NOTIFICATION_DETAILS) as NotificationHelper.ReconnectionNotification
        reconnectionNotification.reconnectionInformation?.let {
            initReconnectionUI(it)
        }
        textDescription.text = reconnectionNotification.content
        textTitle.text = reconnectionNotification.title
        reconnectionNotification.fullScreenDialog?.let {
            it.fullScreenIcon?.let { it1 -> image.setImageResource(it1) }
        }

        layoutUpsell.root.isVisible = reconnectionNotification.fullScreenDialog?.hasUpsellLayout == true
        layoutUpsell.textManyCountries.text = getString(R.string.upsell_many_countries, serverManager.getVpnCountries().size)
        layoutUpsell.textDeviceCount.text = getString(R.string.upsell_device_count, getString(R.string.device_count))
        reconnectionNotification.action?.let { actionItem ->
            buttonUpgrade.text = actionItem.title
            buttonUpgrade.setOnClickListener { openUrl(actionItem.actionUrl) }
            buttonBack.setOnClickListener { finish() }
        } ?: run {
            buttonBack.isVisible = false
            buttonUpgrade.text = getString(R.string.got_it)
            buttonUpgrade.setOnClickListener {
                finish()
            }
        }
    }

    private fun initReconnectionUI(fallbackSwitch: VpnFallbackResult.Switch) =
        with(binding.itemSwitchLayout) {
            root.isVisible = true

            val fromProfile = fallbackSwitch.fromProfile
            val toProfile = fallbackSwitch.toProfile
            fromProfile.wrapper.setDeliverer(serverManager)
            toProfile.wrapper.setDeliverer(serverManager)

            textFromServer.text = fromProfile.server?.serverName
            textToServer.text = toProfile.server?.serverName
            if (toProfile.isSecureCore) {
                imageToCountrySc.setImageResource(
                    CountryTools.getFlagResource(this@SwitchDialogActivity, toProfile.server?.exitCountry)
                )
                imageToCountrySc.isVisible = true
                arrowToSc.isVisible = true
            }
            if (fromProfile.isSecureCore) {
                imageFromCountrySc.setImageResource(
                    CountryTools.getFlagResource(
                        this@SwitchDialogActivity, fromProfile.server?.exitCountry
                    )
                )
                imageFromCountrySc.isVisible = true
                arrowFromSc.isVisible = true
            }
            imageToCountry.setImageResource(
                CountryTools.getFlagResource(this@SwitchDialogActivity, toProfile.server?.entryCountry)
            )
            imageFromCountry.setImageResource(
                CountryTools.getFlagResource(this@SwitchDialogActivity, fromProfile.server?.entryCountry)
            )
        }

    override fun initViewModel() {

    }

    companion object {
        const val EXTRA_NOTIFICATION_DETAILS = "ReconnectionNotification"
    }
}