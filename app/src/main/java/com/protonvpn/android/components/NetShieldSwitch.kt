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
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.CompoundButton
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.asLiveData
import com.protonvpn.android.R
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.databinding.ItemNetshieldBinding
import com.protonvpn.android.models.config.NetShieldProtocol
import com.protonvpn.android.ui.showGenericReconnectDialog
import com.protonvpn.android.ui.planupgrade.UpgradeNetShieldDialogActivity
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnUiDelegate
import com.protonvpn.android.vpn.VpnStateMonitor

class NetShieldSwitch(context: Context, attrs: AttributeSet) : FrameLayout(context, attrs) {

    class ReconnectDialogDelegate(
        private val vpnUiDelegate: VpnUiDelegate,
        private val stateMonitor: VpnStateMonitor,
        private val connectionManager: VpnConnectionManager,
    ) {
        fun needsToReconnect() =
            stateMonitor.isConnected && stateMonitor.connectionProtocol?.localAgentEnabled() == false

        fun reconnectIfNeeded() {
            if (needsToReconnect()) {
                connectionManager.reconnect(vpnUiDelegate)
            }
        }
    }

    private val binding: ItemNetshieldBinding
    private val isInConnectedScreen: Boolean
    private val withReconnectDialog: Boolean
    private var netshieldFreeMode: Boolean = true
    private val toggleDrawables: Array<Drawable>
    private var isInitialStateSet = false
    private lateinit var onChangedCallback: () -> Unit
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

    fun setNetShieldValue(newProtocol: NetShieldProtocol) {
        // Disable the change listener to not propagate the programmatically set value back to UserData.
        binding.switchNetshield.setOnCheckedChangeListener(null)
        onStateChange(newProtocol)
        binding.switchNetshield.setOnCheckedChangeListener { _, _ -> onChangedCallback() }
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

    private fun showReconnectDialog(needsNote: Boolean, reconnectCallback: () -> Unit) {
        val message =
            if (needsNote) R.string.reconnectToEnableNetshield
            else R.string.settingsReconnectToChangeDialogContent
        showGenericReconnectDialog(
            context,
            message,
            PREF_SHOW_NETSHIELD_RECONNECT_DIALOG,
            reconnectCallback = reconnectCallback
        )
    }

    fun init(
        initialValue: NetShieldProtocol,
        appConfig: AppConfig,
        lifecycleOwner: LifecycleOwner,
        isFreeUser: Boolean,
        reconnectDialogDelegate: ReconnectDialogDelegate,
        changeCallback: (protocol: NetShieldProtocol) -> Unit
    ) = with(binding) {
        appConfig.appConfigFlow.asLiveData().observe(lifecycleOwner, Observer {
            root.isVisible = appConfig.getFeatureFlags().netShieldEnabled
        })
        netshieldFreeMode = isFreeUser
        onStateChange(initialValue)
        initUserTier()

        if (netshieldFreeMode) {
            initUserTier()
        } else {
            onChangedCallback = {
                onStateChange(currentState)
                changeCallback(currentState)
                reconnectDialogDelegate.reconnectIfNeeded()
            }
            radioGroupSettings.setOnCheckedChangeListener { _, _ -> onChangedCallback.invoke() }
            switchNetshield.setOnCheckedChangeListener { _, _ -> onChangedCallback.invoke() }

            val dialogInterceptor: CompoundButton.() -> Boolean = {
                val needsNoteOnAdBlocking = when {
                    this == switchNetshield -> !isSwitchedOn && extendedProtocol
                    this == radioFullBlocking -> isSwitchedOn
                    else -> false
                }

                if (withReconnectDialog && reconnectDialogDelegate.needsToReconnect()) {
                    showReconnectDialog(needsNoteOnAdBlocking) {
                        isChecked = !isChecked
                        onStateChange(currentState)
                        changeCallback(currentState)
                        reconnectDialogDelegate.reconnectIfNeeded()
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
        context.startActivity(Intent(context, UpgradeNetShieldDialogActivity::class.java))
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
