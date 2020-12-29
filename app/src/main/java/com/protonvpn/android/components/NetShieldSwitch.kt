/*
 * Copyright (c) 2020 Proton Technologies AG
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
package com.protonvpn.android.components

import android.animation.LayoutTransition
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.TypedArray
import android.net.Uri
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.CompoundButton
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getDrawable
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.Theme
import com.protonvpn.android.R
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.databinding.ItemNetshieldBinding
import com.protonvpn.android.models.config.NetShieldProtocol
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.vpn.VpnStateMonitor

class NetShieldSwitch(context: Context, attrs: AttributeSet) : FrameLayout(context, attrs) {

    private val binding: ItemNetshieldBinding
    private val isInConnectedScreen: Boolean
    private val withReconnectDialog: Boolean
    val currentState: NetShieldProtocol
        get() {
            return if (isSwitchedOn) {
                if (extendedProtocol) {
                    NetShieldProtocol.ENABLED_EXTENDED
                } else {
                    NetShieldProtocol.ENABLED
                }
            } else {
                NetShieldProtocol.DISABLED
            }
        }

    private val isSwitchedOn: Boolean
        get() {
            return binding.switchNetshield.isChecked
        }

    private val extendedProtocol: Boolean
        get() {
            return binding.radioGroup.checkedRadioButtonId == R.id.radioFullBlocking
        }

    fun isSwitchVisible() = binding.root.isVisible

    fun setNetShieldValue(newProtocol: NetShieldProtocol) {
        onStateChange(newProtocol)
    }

    private fun onStateChange(newProtocol: NetShieldProtocol) {
        with(binding) {
            switchNetshield.isChecked = newProtocol != NetShieldProtocol.DISABLED
            netShieldSettings.isVisible = newProtocol != NetShieldProtocol.DISABLED
            if (newProtocol != NetShieldProtocol.DISABLED) {
                val buttonId =
                    if (newProtocol == NetShieldProtocol.ENABLED) R.id.radioSimpleBlocking else R.id.radioFullBlocking
                radioGroup.check(buttonId)
            }
            if (isInConnectedScreen) {
                val netShieldEnabled = newProtocol != NetShieldProtocol.DISABLED
                imageExpand.isVisible = netShieldEnabled
                layoutSummary.isVisible = netShieldEnabled && imageExpand.isChecked
                netShieldSettings.isVisible = netShieldEnabled && !imageExpand.isChecked
                textCollapsedMark.isVisible = netShieldEnabled && imageExpand.isChecked
                val descriptionText =
                    if (currentState == NetShieldProtocol.ENABLED) R.string.netShieldBlockMalwareOnly
                    else R.string.netShieldFullBlock
                textNetDescription.setText(descriptionText)
                layoutNetshield.setBackgroundColor(
                    ContextCompat.getColor(
                        context,
                        if (newProtocol != NetShieldProtocol.DISABLED) R.color.colorAccent else R.color.dimmedGrey
                    )
                )
            } else {
                textCollapsedMark.isVisible = false
            }
        }
    }

    init {
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        binding = ItemNetshieldBinding.inflate(inflater, this, true)
        val attributes = context.obtainStyledAttributes(attrs, R.styleable.NetShieldSwitch)
        isInConnectedScreen = attributes.getBoolean(R.styleable.NetShieldSwitch_isInConnectedScreen, false)
        withReconnectDialog = attributes.getBoolean(
            R.styleable.NetShieldSwitch_withReconnectConfirmation, true
        )
        val layoutTransition = LayoutTransition()
        layoutTransition.disableTransitionType(LayoutTransition.DISAPPEARING)
        binding.layoutNetshield.layoutTransition = layoutTransition
        initAttributes(attributes)
        attributes.recycle()
    }

    private fun initAttributes(attributes: TypedArray) {
        with(binding) {
            if (!attributes.getBoolean(R.styleable.NetShieldSwitch_withPadding, true)) {
                layoutNetshield.setPadding(0)
            }
            textNetDescription.text = attributes.getString(R.styleable.NetShieldSwitch_descriptionText)
            if (isInConnectedScreen) {
                layoutNetshield.setOnClickListener {
                    imageExpand.isChecked = !imageExpand.isChecked
                    onStateChange(currentState)
                }
                textNetDescription.setTextColor(ContextCompat.getColor(context, R.color.grey))
                switchNetshield.setTextColor(ContextCompat.getColor(context, R.color.grey))
            } else {
                switchNetshield.trackTintList = ContextCompat.getColorStateList(context, R.color.switch_track)
            }
            imageExpand.isVisible = isInConnectedScreen
            tintRadioButtons()
        }
    }

    private fun tintRadioButtons() = with(binding) {
        val notSelectedColor = if (isInConnectedScreen) R.color.grey else R.color.white
        val selectedColor = if (isInConnectedScreen) R.color.grey else R.color.colorAccent
        val colorStateList = ColorStateList(
            arrayOf(
                intArrayOf(-android.R.attr.state_checked), intArrayOf(android.R.attr.state_checked)
            ), intArrayOf(
                ContextCompat.getColor(context, notSelectedColor),
                ContextCompat.getColor(context, selectedColor)
            )
        )

        radioSimpleBlocking.buttonTintList = colorStateList
        radioSimpleBlocking.setTextColor(ContextCompat.getColor(context, notSelectedColor))

        radioFullBlocking.buttonTintList = colorStateList
        radioFullBlocking.setTextColor(ContextCompat.getColor(context, notSelectedColor))
    }

    private fun showReconnectDialog(isRadioButton: Boolean, changeCallback: (agreedToChange: Boolean) -> Unit) {
        MaterialDialog.Builder(context).theme(Theme.DARK)
            .checkBoxPrompt(context.getString(R.string.dialogDontShowAgain), false) { _, checked ->
                Storage.saveBoolean(PREF_SHOW_NETSHIELD_RECONNECT_DIALOG, !checked)
            }
            .icon(getDrawable(context, R.drawable.ic_refresh)!!)
            .canceledOnTouchOutside(false)
            .title(R.string.netShieldReconnectionNeeded)
            .content(
                if (!isSwitchedOn || isRadioButton)
                    R.string.netShieldReconnectionDescription
                else
                    R.string.netShieldReconnectionDescriptionDisabling
            )
            .positiveText(R.string.reconnect)
            .onPositive { _: MaterialDialog?, _: DialogAction? ->
                changeCallback(true)
            }
            .negativeText(R.string.cancel)
            .onNegative { _, _ ->
                changeCallback(false)
            }.show()
    }

    fun init(
        initialValue: NetShieldProtocol,
        appConfig: AppConfig,
        lifecycleOwner: LifecycleOwner,
        userData: UserData,
        stateMonitor: VpnStateMonitor,
        changeCallback: (protocol: NetShieldProtocol) -> Unit
    ) = with(binding) {
        appConfig.getLiveConfig().observe(lifecycleOwner, Observer {
            root.isVisible = appConfig.getFeatureFlags().netShieldEnabled
        })
        onStateChange(initialValue)
        initUserTier(userData)
        val checkedChangeListener = {
            onStateChange(currentState)
            changeCallback(currentState)
            checkForReconnection(stateMonitor)
        }
        radioGroup.setOnCheckedChangeListener { _, _ -> checkedChangeListener.invoke() }
        switchNetshield.setOnCheckedChangeListener { _, _ -> checkedChangeListener.invoke() }

        val dialogInterceptor: CompoundButton.() -> Boolean = {
            val needsReconnectDialog = withReconnectDialog &&
                Storage.getBoolean(PREF_SHOW_NETSHIELD_RECONNECT_DIALOG, true)
            if (stateMonitor.isConnected && needsReconnectDialog) {
                showReconnectDialog(this is RadioButtonEx) { agreedToReconnect ->
                    if (agreedToReconnect) {
                        isChecked = !isChecked
                        onStateChange(currentState)
                        changeCallback(currentState)
                        checkForReconnection(stateMonitor)
                    }
                }
                true
            } else
                false
        }
        radioFullBlocking.switchClickInterceptor = dialogInterceptor
        radioSimpleBlocking.switchClickInterceptor = dialogInterceptor
        switchNetshield.switchClickInterceptor = dialogInterceptor
    }

    private fun initUserTier(userData: UserData) = with(binding) {
        val netshieldPartialMode = userData.isFreeUser
        plusFeature.isVisible = netshieldPartialMode
        if (netshieldPartialMode) {
            radioFullBlocking.isEnabled = false
            val disabledColor = if (isInConnectedScreen) R.color.brightGrey else R.color.lightGrey
            radioFullBlocking.setTextColor(ContextCompat.getColor(context, disabledColor))
            val colorStateList = ColorStateList(
                arrayOf(
                    intArrayOf(-android.R.attr.state_checked), intArrayOf(android.R.attr.state_checked)
                ), intArrayOf(
                    ContextCompat.getColor(context, disabledColor),
                    ContextCompat.getColor(context, disabledColor)
                )
            )

            radioFullBlocking.buttonTintList = colorStateList
            plusFeature.setOnClickListener { showUpgradeDialog() }
        }
    }

    private fun showUpgradeDialog() {
        MaterialDialog.Builder(context).theme(Theme.DARK)
            .icon(getDrawable(context, R.drawable.ic_upgrade)!!)
            .title(R.string.upgradeRequired)
            .content(R.string.netShieldPaidFeature)
            .positiveText(R.string.upgrade)
            .onPositive { _: MaterialDialog?, _: DialogAction? ->
                val browserIntent = Intent(Intent.ACTION_VIEW,
                    Uri.parse(Constants.DASHBOARD_URL))
                context.startActivity(browserIntent)
            }
            .negativeText(R.string.cancel)
            .show()
    }

    private fun checkForReconnection(stateMonitor: VpnStateMonitor) {
        if (stateMonitor.isConnected) {
            stateMonitor.reconnect(context)
        }
    }

    companion object {
        const val PREF_SHOW_NETSHIELD_RECONNECT_DIALOG = "PREF_SHOW_NETSHIELD_RECONNECT_DIALOG"
    }
}
