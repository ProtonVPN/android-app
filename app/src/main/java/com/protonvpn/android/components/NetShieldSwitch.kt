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

import android.content.Context
import android.content.Intent
import android.content.res.TypedArray
import android.net.Uri
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.CompoundButton
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.Theme
import com.protonvpn.android.R
import com.protonvpn.android.databinding.ItemNetshieldBinding
import com.protonvpn.android.models.config.NetShieldProtocol
import com.protonvpn.android.models.config.UserData
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

    private var extendedProtocol: Boolean = false

    private fun onStateChange(newProtocol: NetShieldProtocol) {
        with(binding) {
            switchNetshield.isChecked = newProtocol != NetShieldProtocol.DISABLED
            netShieldSettings.isVisible = newProtocol != NetShieldProtocol.DISABLED
            if (newProtocol != NetShieldProtocol.DISABLED) {
                radioSimpleBlocking.isChecked = newProtocol == NetShieldProtocol.ENABLED
                radioFullBlocking.isChecked = newProtocol == NetShieldProtocol.ENABLED_EXTENDED
            }
            if (isInConnectedScreen) {
                textNetDescription.isVisible = newProtocol != NetShieldProtocol.DISABLED
                netShieldSettings.isVisible = false
                layoutNetshield.setBackgroundColor(
                        ContextCompat.getColor(context,
                                if (newProtocol != NetShieldProtocol.DISABLED) R.color.colorAccent else R.color
                                        .dimmedGrey))
            }
        }
    }

    init {
        val inflater =
                context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        binding = ItemNetshieldBinding.inflate(inflater, this,
                true)
        val attributes = context.obtainStyledAttributes(attrs, R.styleable.NetShieldSwitch)
        isInConnectedScreen = attributes.getBoolean(R.styleable.NetShieldSwitch_isInConnectedScreen, false)
        withReconnectDialog = attributes.getBoolean(R.styleable
                .NetShieldSwitch_withReconnectConfirmation, true)
        initAttributes(attributes)
        attributes.recycle()
    }

    private fun initAttributes(attributes: TypedArray) {
        with(binding) {
            if (!attributes.getBoolean(R.styleable.NetShieldSwitch_withPadding, true)) {
                layoutNetshield.setPadding(0)
            }
            switchNetshield.setTextColor(attributes.getColor(R.styleable.NetShieldSwitch_titleTextColor,
                    ContextCompat.getColor(context, R.color.white)))
            textNetDescription.text = attributes.getString(R.styleable.NetShieldSwitch_descriptionText)
            if (isInConnectedScreen) {
                val textColor = ContextCompat.getColor(context, R.color.white)
                switchNetshield.setTextColor(textColor)
                textNetDescription.setTextColor(textColor)
                netShieldSettings.visibility = View.GONE
            } else {
                switchNetshield.trackTintList = ContextCompat.getColorStateList(context, R.color.switch_track)
            }
        }
    }

    private fun showReconnectDialog(changeCallback: (agreedToChange: Boolean) -> Unit) {
        MaterialDialog.Builder(context).theme(Theme.DARK)
                .checkBoxPrompt(context.getString(R.string.dialogDontShowAgain), false) { _, checked ->
                    Storage.saveBoolean(PREF_SHOW_NETSHIELD_RECONNECT_DIALOG, !checked)
                }
                .title(R.string.netShieldReconnectionNeeded)
                .content(R.string.netShieldReconnectionDescription)
                .positiveText(R.string.reconnect)
                .onPositive { _: MaterialDialog?, _: DialogAction? ->
                    changeCallback(true)
                }
                .negativeText(R.string.cancel)
                .onNegative { _, _ ->
                    changeCallback(false)
                }.show()
    }

    fun init(initialValue: NetShieldProtocol, userData: UserData, stateMonitor: VpnStateMonitor,
             changeCallback: (protocol: NetShieldProtocol) -> Unit) = with(binding) {
        onStateChange(initialValue)
        initUserTier(userData)
        switchNetshield.isChecked = initialValue != NetShieldProtocol.DISABLED
        val checkedChangeListener = CompoundButton.OnCheckedChangeListener { view, _ ->
            if (view.isPressed) {
                if (stateMonitor.isConnected && withReconnectDialog
                        && Storage.getBoolean(PREF_SHOW_NETSHIELD_RECONNECT_DIALOG, true)) {
                    showReconnectDialog { agreedToReconnect ->
                        if (agreedToReconnect) {
                            onStateChange(currentState)
                            changeCallback(currentState)
                            checkForReconnection(stateMonitor)
                        } else {
                            view.isChecked = !view.isChecked
                            onStateChange(currentState)
                        }
                    }
                } else {
                    onStateChange(currentState)
                    changeCallback(currentState)
                    checkForReconnection(stateMonitor)
                }
            }
        }
        radioFullBlocking.setOnCheckedChangeListener { buttonView, isChecked ->
            extendedProtocol = isChecked
            if (buttonView.isPressed) {
                checkedChangeListener.onCheckedChanged(buttonView, isChecked)
            }
        }
        radioSimpleBlocking.setOnCheckedChangeListener { buttonView, isChecked ->
            extendedProtocol = !isChecked
            if (buttonView.isPressed) {
                checkedChangeListener.onCheckedChanged(buttonView, isChecked)
            }
        }
        extendedProtocol = initialValue == NetShieldProtocol.ENABLED_EXTENDED
        switchNetshield.setOnCheckedChangeListener(checkedChangeListener)
    }

    private fun initUserTier(userData: UserData) = with(binding) {
        if (!userData.isUserPlusOrAbove) {
            radioFullBlocking.isEnabled = false
            plusFeature.setOnClickListener {
                MaterialDialog.Builder(context).theme(Theme.DARK)
                        .title(R.string.paidFeature)
                        .content(R.string.netShieldPaidFeature)
                        .positiveText(R.string.upgrade)
                        .onPositive { _: MaterialDialog?, _: DialogAction? ->
                            val browserIntent = Intent(Intent.ACTION_VIEW,
                                    Uri.parse("https://account.protonvpn.com/dashboard"))
                            context.startActivity(browserIntent)
                        }
                        .negativeText(R.string.cancel)
                        .show()
            }
        }
    }

    private fun checkForReconnection(stateMonitor: VpnStateMonitor) {
        if (stateMonitor.isConnected) {
            val currentConnection = stateMonitor.connectionProfile
            currentConnection?.let {
                it.setNetShieldProtocol(currentState)
                stateMonitor.disconnectWithCallback { stateMonitor.connect(context, it) }
            }
        }
    }

    companion object {
        const val PREF_SHOW_NETSHIELD_RECONNECT_DIALOG = "PREF_SHOW_NETSHIELD_RECONNECT_DIALOG"
    }
}