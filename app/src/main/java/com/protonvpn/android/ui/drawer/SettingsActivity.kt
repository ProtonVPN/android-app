/*
 * Copyright (c) 2018 Proton Technologies AG
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
package com.protonvpn.android.ui.drawer

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ScrollView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import br.com.sapereaude.maskedEditText.MaskedEditText
import butterknife.BindView
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.Theme
import com.google.android.material.snackbar.Snackbar
import com.protonvpn.android.R
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.bus.EventBus
import com.protonvpn.android.bus.StatusSettingChanged
import com.protonvpn.android.components.BaseActivity
import com.protonvpn.android.components.ContentLayout
import com.protonvpn.android.components.EditTextValidator
import com.protonvpn.android.components.NetShieldSwitch
import com.protonvpn.android.components.ProtocolSelection
import com.protonvpn.android.components.ProtonSpinner
import com.protonvpn.android.components.ProtonSwitch
import com.protonvpn.android.components.SplitTunnelButton
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.HtmlTools
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.ViewUtils.hideKeyboard
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnStateMonitor
import javax.inject.Inject

@ContentLayout(R.layout.activity_settings)
class SettingsActivity : BaseActivity() {

    @BindView(R.id.spinnerDefaultConnection)
    lateinit var spinnerDefaultConnection: ProtonSpinner<Profile>
    @BindView(R.id.switchAutoStart) lateinit var switchAutoStart: ProtonSwitch
    @BindView(R.id.textMTU) lateinit var textMTU: MaskedEditText
    @BindView(R.id.switchShowIcon) lateinit var switchShowIcon: ProtonSwitch
    @BindView(R.id.switchDnsLeak) lateinit var switchDnsLeak: ProtonSwitch
    @BindView(R.id.switchBypassLocal) lateinit var switchBypassLocal: ProtonSwitch
    @BindView(R.id.switchShowSplitTunnel) lateinit var switchShowSplitTunnel: ProtonSwitch
    @BindView(R.id.switchDnsOverHttps) lateinit var switchDnsOverHttps: ProtonSwitch
    @BindView(R.id.protocolSelection) lateinit var protocolSelection: ProtocolSelection
    @BindView(R.id.splitTunnelLayout) lateinit var splitTunnelLayout: View
    @BindView(R.id.scrollView) lateinit var scrollView: NestedScrollView
    @BindView(R.id.splitTunnelIPs) lateinit var splitTunnelIPs: SplitTunnelButton
    @BindView(R.id.splitTunnelApps) lateinit var splitTunnelApps: SplitTunnelButton
    @BindView(R.id.buttonAlwaysOn) lateinit var buttonAlwaysOn: ProtonSwitch
    @BindView(R.id.buttonLicenses) lateinit var buttonLicenses: ProtonSwitch
    @BindView(R.id.netShieldSwitch) lateinit var switchNetShield: NetShieldSwitch
    @BindView(R.id.switchVpnAccelerator) lateinit var switchVpnAccelerator: ProtonSwitch
    @BindView(R.id.switchVpnAcceleratorNotifications) lateinit var switchVpnAcceleratorNotifications: ProtonSwitch
    @Inject lateinit var serverManager: ServerManager
    @Inject lateinit var stateMonitor: VpnStateMonitor
    @Inject lateinit var connectionManager: VpnConnectionManager
    @Inject lateinit var userPrefs: UserData
    @Inject lateinit var appConfig: AppConfig

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initToolbarWithUpEnabled()
        initSettings()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initSettings() {
        initOSRelatedVisibility()
        initMTUField()
        splitTunnelApps.initTextUpdater(this, userPrefs)
        splitTunnelIPs.initTextUpdater(this, userPrefs)
        splitTunnelIPs.buttonManage.contentDescription = getString(R.string.settingsExcludeIPAddresses)
        buttonAlwaysOn.setOnClickListener { navigateTo(AlwaysOnSettingsActivity::class.java); }
        switchAutoStart.switchProton.isChecked = userPrefs.connectOnBoot
        switchAutoStart.switchProton
                .setOnCheckedChangeListener { _, isChecked ->
                    userPrefs.connectOnBoot = isChecked
                }
        switchNetShield.init(userPrefs.netShieldProtocol, appConfig, this, userPrefs, stateMonitor, connectionManager) {
            userPrefs.netShieldProtocol = it
        }
        switchShowIcon.switchProton.isChecked = userPrefs.shouldShowIcon()
        switchShowIcon.switchProton
                .setOnCheckedChangeListener { _, isChecked ->
                    userPrefs.setShowIcon(isChecked)
                    EventBus.getInstance().post(StatusSettingChanged(isChecked))
                }

        switchDnsLeak.isEnabled = false

        switchDnsOverHttps.setDescription(HtmlTools.fromHtml(getString(
                R.string.settingsAllowAlternativeRoutingDescription, Constants.ALTERNATIVE_ROUTING_LEARN_URL)))
        switchDnsOverHttps.switchProton.isChecked = userPrefs.apiUseDoH
        switchDnsOverHttps.switchProton.setOnCheckedChangeListener { _, isChecked ->
            userPrefs.apiUseDoH = isChecked
        }

        protocolSelection.init(userPrefs.useSmartProtocol, userPrefs.manualProtocol,
                userPrefs.transmissionProtocol, showWireguardWarning = true) {
            userPrefs.useSmartProtocol = protocolSelection.useSmart
            userPrefs.manualProtocol = protocolSelection.manualProtocol
            userPrefs.transmissionProtocol = protocolSelection.transmissionProtocol
            initSplitTunneling(userPrefs.useSplitTunneling)
        }
        spinnerDefaultConnection.setItems(serverManager.getSavedProfiles())
        spinnerDefaultConnection.selectedItem = serverManager.defaultConnection
        spinnerDefaultConnection.setOnItemSelectedListener { item, _ ->
            userPrefs.defaultConnection = item
        }

        var snackBar: Snackbar? = null
        val useSplitTunnel = userPrefs.useSplitTunneling
        val disableWhenConnectedListener = { _: View, _: MotionEvent ->
            if (stateMonitor.isConnected) {
                // Creating snackbar without showing it might lead to leaking activity.
                // Fixed in 1.1.0-alpha of material library.
                if (snackBar == null) {
                    snackBar = Snackbar.make(findViewById(R.id.coordinator),
                            R.string.settingsCannotChangeWhileConnected, Snackbar.LENGTH_LONG)
                }
                if (snackBar?.isShownOrQueued == false) {
                    snackBar?.show()
                }
                true
            } else false
        }
        textMTU.setOnTouchListener(disableWhenConnectedListener)

        protocolSelection.setTouchBlocker(disableWhenConnectedListener)

        initSplitTunneling(useSplitTunnel)
        switchShowSplitTunnel.switchProton.contentDescription =
                getString(R.string.splitTunnellingSwitch)
        switchShowSplitTunnel.switchProton.setOnTouchListener(disableWhenConnectedListener)
        splitTunnelApps.buttonManage.setOnTouchListener(disableWhenConnectedListener)
        splitTunnelIPs.buttonManage.setOnTouchListener(disableWhenConnectedListener)
        switchShowSplitTunnel.switchProton.isChecked = useSplitTunnel
        switchShowSplitTunnel.switchProton.setOnCheckedChangeListener { _, isChecked ->
            initSplitTunneling(isChecked)
            userPrefs.useSplitTunneling = isChecked
            scrollView.postDelayed({ scrollView.fullScroll(ScrollView.FOCUS_DOWN) }, 100)
        }

        switchBypassLocal.switchProton.isChecked = userPrefs.bypassLocalTraffic()
        switchBypassLocal.switchProton.setOnTouchListener(disableWhenConnectedListener)
        switchBypassLocal.switchProton.setOnCheckedChangeListener { _, isChecked ->
            userPrefs.setBypassLocalTraffic(isChecked)
        }

        initVpnAcceleratorToggles()

        buttonLicenses.setOnClickListener {
            navigateTo(OssLicensesActivity::class.java)
        }
    }

    private fun initVpnAcceleratorToggles() {
        if (appConfig.getFeatureFlags().vpnAccelerator) {
            updateVpnAcceleratorToggles()
            switchVpnAccelerator.switchProton.switchClickInterceptor = {
                tryToggleVpnAccelerator()
                true
            }
            userPrefs.updateEvent.observe(this) {
                updateVpnAcceleratorToggles()
            }

            switchVpnAcceleratorNotifications.isVisible = userPrefs.isVpnAcceleratorEnabled
            switchVpnAcceleratorNotifications.switchProton.isChecked =
                userPrefs.showVpnAcceleratorNotifications()
            switchVpnAcceleratorNotifications.switchProton.setOnCheckedChangeListener { _, isChecked ->
                userPrefs.setShowVpnAcceleratorNotifications(isChecked)
            }
        } else {
            switchVpnAccelerator.isVisible = false
            switchVpnAcceleratorNotifications.isVisible = false
        }
    }

    private fun initMTUField() {
        val defaultMtu = getString(R.string.settingsDefaultMtu).toInt()
        textMTU.setText(if (userPrefs.mtuSize != defaultMtu) userPrefs.mtuSize.toString() else "")
        textMTU.addTextChangedListener(object : EditTextValidator(textMTU) {
            override fun validate(textView: EditText, text: String) {
                val textToInt = if (text.isEmpty()) 0 else text.toInt()
                if (textToInt < 1280 || textToInt > 1500) {
                    textView.error = getString(R.string.settingsMtuRangeInvalid)
                } else {
                    textView.error = null
                    val input =
                            if (textMTU.rawText.isEmpty()) defaultMtu else textMTU.rawText.toInt()
                    if (input in 1280..1500) {
                        userPrefs.mtuSize = input
                    }
                }
            }
        })

        textMTU.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard()
                true
            } else {
                false
            }
        }
    }

    private fun initOSRelatedVisibility() {
        switchAutoStart.visibility = if (Build.VERSION.SDK_INT >= 24) GONE else VISIBLE
        buttonAlwaysOn.visibility = if (Build.VERSION.SDK_INT >= 24) VISIBLE else GONE
        switchShowIcon.visibility = if (Build.VERSION.SDK_INT >= 26) GONE else VISIBLE
    }

    private fun initSplitTunneling(isChecked: Boolean) {
        splitTunnelLayout.visibility = if (isChecked) VISIBLE else GONE
        switchShowSplitTunnel.setDividerVisibility(if (isChecked) GONE else VISIBLE)
    }

    private fun updateVpnAcceleratorToggles() {
        val isEnabled = userPrefs.isVpnAcceleratorEnabled
        switchVpnAccelerator.switchProton.isChecked = isEnabled
        switchVpnAcceleratorNotifications.isVisible = isEnabled
    }

    private fun tryToggleVpnAccelerator() {
        if (stateMonitor.isEstablishingOrConnected) {
            MaterialDialog.Builder(this).theme(Theme.DARK)
                .icon(ContextCompat.getDrawable(context, R.drawable.ic_refresh)!!)
                .title(R.string.dialogTitleReconnectionNeeded)
                .content(R.string.settingsSmartReconnectReconnectDialogContent)
                .positiveText(R.string.reconnect)
                .onPositive { _: MaterialDialog?, _: DialogAction? ->
                    toggleVpnAccelerator()
                    connectionManager.reconnect(this)
                }
                .negativeText(R.string.cancel)
                .show()
        } else {
            toggleVpnAccelerator()
        }
    }

    private fun toggleVpnAccelerator() {
        userPrefs.isVpnAcceleratorEnabled = !userPrefs.isVpnAcceleratorEnabled
    }
}
