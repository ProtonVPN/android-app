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

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.viewModels
import androidx.core.view.isVisible
import com.google.android.material.snackbar.Snackbar
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseActivityV2
import com.protonvpn.android.components.NotificationHelper
import com.protonvpn.android.databinding.ActivitySwitchDialogBinding
import com.protonvpn.android.ui.snackbar.DelegatedSnackManager
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.utils.ServerManager
import dagger.hilt.android.AndroidEntryPoint
import me.proton.core.presentation.utils.SnackType
import javax.inject.Inject

@AndroidEntryPoint
class SwitchDialogActivity : BaseActivityV2() {

    private val viewModel: SwitchDialogViewModel by viewModels()

    @Inject lateinit var serverManager: ServerManager

    class CloseOnSuccessContract(val intent: Intent) : ActivityResultContract<Unit, Boolean>() {
        override fun createIntent(context: Context, input: Unit) = intent
        override fun parseResult(resultCode: Int, result: Intent?) = resultCode == Activity.RESULT_OK
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySwitchDialogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initUI(binding)
    }

    private fun initUI(binding: ActivitySwitchDialogBinding) = with(binding) {
        val reconnectionNotification =
            intent.getParcelableExtra<NotificationHelper.ReconnectionNotification>(EXTRA_NOTIFICATION_DETAILS)!!
        reconnectionNotification.reconnectionInformation?.let {
            initReconnectionUI(binding, it)
        }
        textDescription.text = reconnectionNotification.content
        textTitle.text = reconnectionNotification.title
        reconnectionNotification.fullScreenDialog?.let { fullScreenDialog ->
            buttonBack.setOnClickListener {
                fullScreenDialog.cancelToastMessage?.let { message ->
                    delegatedSnackManager.postSnack(
                        message,
                        SnackType.Success,
                        DelegatedSnackManager.SnackActionType.GOT_IT,
                        Snackbar.LENGTH_INDEFINITE
                    )
                }
                finish()
            }
            fullScreenDialog.fullScreenIcon?.let { it1 -> image.setImageResource(it1) }
        }

        layoutUpsell.root.isVisible = reconnectionNotification.fullScreenDialog?.hasUpsellLayout == true
        layoutUpsell.textManyCountries.text = getManyServersInManyCountriesText()
        reconnectionNotification.action?.let { actionItem ->
            initActionButton(binding, actionItem)
        } ?: run {
            buttonBack.isVisible = false
            buttonUpgrade.text = getString(R.string.got_it)
            buttonUpgrade.setOnClickListener {
                finish()
            }
        }
    }

    private fun initActionButton(binding: ActivitySwitchDialogBinding, actionItem: NotificationHelper.ActionItem) =
        with(binding) {
            buttonUpgrade.text = actionItem.title
            when (actionItem) {
                is NotificationHelper.ActionItem.Activity -> {
                    val launcher = if (actionItem.closeAfterSuccess)
                        registerForActivityResult(CloseOnSuccessContract(actionItem.activityIntent)) {
                            if (it) finish()
                        } else null
                    val showUpgrade = viewModel.showUpgrade()
                    buttonUpgrade.isVisible = showUpgrade
                    if (!showUpgrade)
                        buttonBack.setText(R.string.close)
                    buttonUpgrade.setOnClickListener {
                        if (launcher != null)
                            launcher.launch(Unit)
                        else
                            startActivity(actionItem.activityIntent)
                    }
                }
                is NotificationHelper.ActionItem.BgAction ->
                    buttonUpgrade.setOnClickListener {
                        actionItem.pendingIntent.send()
                    }
            }
        }

    private fun initReconnectionUI(
        binding: ActivitySwitchDialogBinding,
        reconnectionInformation: NotificationHelper.ReconnectionInformation
    ) = with(binding.itemSwitchLayout) {
        root.isVisible = true

        textFromServer.text = reconnectionInformation.fromServerName
        textToServer.text = reconnectionInformation.toServerName
        reconnectionInformation.toCountrySecureCore?.let {
            imageToCountrySc.setImageResource(
                CountryTools.getFlagResource(this@SwitchDialogActivity, it)
            )
            imageToCountrySc.isVisible = true
            arrowToSc.isVisible = true
        }
        reconnectionInformation.fromCountrySecureCore?.let {
            imageFromCountrySc.setImageResource(
                CountryTools.getFlagResource(
                    this@SwitchDialogActivity, it
                )
            )
            imageFromCountrySc.isVisible = true
            arrowFromSc.isVisible = true
        }
        imageToCountry.setImageResource(
            CountryTools.getFlagResource(this@SwitchDialogActivity, reconnectionInformation.toCountry)
        )
        imageFromCountry.setImageResource(
            CountryTools.getFlagResource(this@SwitchDialogActivity, reconnectionInformation.fromCountry)
        )
    }

    @SuppressLint("SetTextI18n")
    private fun getManyServersInManyCountriesText(): String {
        val serversText = resources.getQuantityString(
            R.plurals.upsell_many_servers,
            serverManager.allServerCount,
            serverManager.allServerCount
        )
        val countriesText = resources.getQuantityString(
            R.plurals.upsell_many_countries,
            serverManager.getVpnCountries().size,
            serverManager.getVpnCountries().size
        )
        return "$serversText, $countriesText"
    }

    companion object {
        const val EXTRA_NOTIFICATION_DETAILS = "ReconnectionNotification"
    }
}
