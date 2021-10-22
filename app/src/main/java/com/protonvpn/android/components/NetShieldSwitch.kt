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
import android.content.res.ColorStateList
import android.content.res.TypedArray
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.CompoundButton
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.protonvpn.android.R
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.databinding.ItemNetshieldBinding
import com.protonvpn.android.models.config.NetShieldProtocol
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.ui.showGenericReconnectDialog
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnStateMonitor

class NetShieldSwitch(context: Context, attrs: AttributeSet) : FrameLayout(context, attrs) {

    private val binding: ItemNetshieldBinding
    private val isInConnectedScreen: Boolean
    private val withReconnectDialog: Boolean
    private var netshieldFreeMode: Boolean = true
    private val toggleDrawables: Array<Drawable>
    private var isInitialStateSet = false
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
    var radiosExpanded: Boolean = false
        set(value) {
            field = value
            onStateChange(currentState)
        }
    var onRadiosExpandClicked: (() -> Unit)? = null

    private val isSwitchedOn: Boolean
        get() {
            return binding.switchNetshield.isChecked
        }

    private val extendedProtocol: Boolean
        get() {
            return binding.radioGroupSettings.checkedRadioButtonId == R.id.radioFullBlocking
        }

    fun isSwitchVisible() = binding.root.isVisible

    fun setNetShieldValue(newProtocol: NetShieldProtocol) {
        onStateChange(newProtocol)
    }

    private fun onStateChange(newProtocol: NetShieldProtocol) {
        with(binding) {
            val netShieldEnabled = newProtocol != NetShieldProtocol.DISABLED
            switchNetshield.isChecked = netShieldEnabled
            radioGroupSettings.isVisible = netShieldEnabled
            if (netShieldEnabled) {
                val buttonId =
                    if (newProtocol == NetShieldProtocol.ENABLED) R.id.radioSimpleBlocking else R.id.radioFullBlocking
                radioGroupSettings.check(buttonId)
            }
            if (isInConnectedScreen) {
                layoutNetshield.isClickable = netShieldEnabled
                showExpandToggle(netShieldEnabled && !netshieldFreeMode)
                textNetShieldTitle.isChecked = radiosExpanded
                textNetDescription.isVisible = netShieldEnabled && !radiosExpanded
                radioGroupSettings.isVisible = (netShieldEnabled || netshieldFreeMode) && radiosExpanded
                val descriptionText =
                    if (currentState == NetShieldProtocol.ENABLED) R.string.netShieldBlockMalwareOnly
                    else R.string.netShieldFullBlock
                textNetDescription.setText(descriptionText)
            }
            if (!isInitialStateSet) radioGroupSettings.jumpDrawablesToCurrentState()
        }
        isInitialStateSet = true
    }

    init {
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        binding = ItemNetshieldBinding.inflate(inflater, this, true)
        val attributes = context.obtainStyledAttributes(attrs, R.styleable.NetShieldSwitch)
        isInConnectedScreen = attributes.getBoolean(R.styleable.NetShieldSwitch_isInConnectedScreen, false)
        withReconnectDialog = attributes.getBoolean(
            R.styleable.NetShieldSwitch_withReconnectConfirmation, true
        )
        toggleDrawables = binding.textNetShieldTitle.compoundDrawablesRelative
        initAttributes(attributes)
        attributes.recycle()
    }

    private fun initAttributes(attributes: TypedArray) {
        with(binding) {
            textNetDescription.text = attributes.getString(R.styleable.NetShieldSwitch_descriptionText)
            if (isInConnectedScreen) {
                layoutNetshield.setOnClickListener {
                    onRadiosExpandClicked?.invoke()
                }
                textNetDescription.updateLayoutParams<MarginLayoutParams> { topMargin = 0 }
            }
            showExpandToggle(isInConnectedScreen)
        }
    }

    private fun showReconnectDialog(reconnectCallback: () -> Unit) {
        showGenericReconnectDialog(
            context,
            R.string.settingsReconnectToChangeDialogContent,
            PREF_SHOW_NETSHIELD_RECONNECT_DIALOG,
            reconnectCallback = reconnectCallback
        )
    }

    fun init(
        initialValue: NetShieldProtocol,
        appConfig: AppConfig,
        lifecycleOwner: LifecycleOwner,
        userData: UserData,
        stateMonitor: VpnStateMonitor,
        connectionManager: VpnConnectionManager,
        changeCallback: (protocol: NetShieldProtocol) -> Unit
    ) = with(binding) {
        appConfig.getLiveConfig().observe(lifecycleOwner, Observer {
            root.isVisible = appConfig.getFeatureFlags().netShieldEnabled
        })
        netshieldFreeMode = userData.isFreeUser
        onStateChange(initialValue)
        initUserTier()

        if (netshieldFreeMode) {
            initUserTier()
        } else {
            val checkedChangeListener = {
                onStateChange(currentState)
                changeCallback(currentState)
                checkForReconnection(stateMonitor, connectionManager)
            }
            radioGroupSettings.setOnCheckedChangeListener { _, _ -> checkedChangeListener.invoke() }
            switchNetshield.setOnCheckedChangeListener { _, _ -> checkedChangeListener.invoke() }

            val dialogInterceptor: CompoundButton.() -> Boolean = {
                val needsReconnectDialog =
                    withReconnectDialog && stateMonitor.connectionProtocol?.localAgentEnabled() == false

                if (stateMonitor.isConnected && needsReconnectDialog) {
                    showReconnectDialog {
                        isChecked = !isChecked
                        onStateChange(currentState)
                        changeCallback(currentState)
                        checkForReconnection(stateMonitor, connectionManager)
                    }
                    true
                } else false
            }
            radioFullBlocking.switchClickInterceptor = dialogInterceptor
            radioSimpleBlocking.switchClickInterceptor = dialogInterceptor
            switchNetshield.switchClickInterceptor = dialogInterceptor
        }
    }

    private fun initUserTier() = with(binding) {
        plusFeature.isVisible = netshieldFreeMode
        switchNetshield.isVisible = !netshieldFreeMode
        if (netshieldFreeMode) {
            radioGroupSettings.isVisible = !isInConnectedScreen
            showExpandToggle(isInConnectedScreen)
            radiosExpanded = true
            disableCheckBox(radioFullBlocking)
            disableCheckBox(radioSimpleBlocking)
            plusFeature.setOnClickListener { showUpgradeDialog() }
        }
    }

    private fun disableCheckBox(view: RadioButtonEx) {
        view.setTextColor(ContextCompat.getColor(context, R.color.white))
        val colorStateList = ColorStateList(
            arrayOf(
                intArrayOf(-android.R.attr.state_checked), intArrayOf(android.R.attr.state_checked)
            ), intArrayOf(
                ContextCompat.getColor(context, R.color.white), ContextCompat.getColor(context, R.color.white)
            )
        )
        view.switchClickInterceptor = {
            showUpgradeDialog()
            true
        }

        view.buttonTintList = colorStateList
    }

    private fun showUpgradeDialog() {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.upgradeRequired)
            .setMessage(R.string.netShieldPaidFeature)
            .setPositiveButton(R.string.upgrade) { _, _ ->
                val browserIntent = Intent(Intent.ACTION_VIEW,
                    Uri.parse(Constants.DASHBOARD_URL))
                context.startActivity(browserIntent)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun checkForReconnection(stateMonitor: VpnStateMonitor, connectionManager: VpnConnectionManager) {
        if (stateMonitor.isConnected && stateMonitor.connectionProtocol?.localAgentEnabled() == false) {
            connectionManager.reconnect(context)
        }
    }

    private fun showExpandToggle(show: Boolean) {
        val title = binding.textNetShieldTitle
        if (show) {
            val (s, t, e, b) = toggleDrawables
            title.setCompoundDrawablesRelativeWithIntrinsicBounds(s, t, e, b)
        } else {
            title.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null)
        }
    }

    companion object {
        const val PREF_SHOW_NETSHIELD_RECONNECT_DIALOG = "PREF_SHOW_NETSHIELD_RECONNECT_DIALOG"
    }
}
