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
import android.widget.ScrollView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
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
import com.protonvpn.android.components.InstalledAppsProvider
import com.protonvpn.android.components.NetShieldSwitch
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.ui.ProtocolSelection
import com.protonvpn.android.ui.ProtocolSelectionActivity
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.HtmlTools
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.sortedByLocaleAware
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnStateMonitor
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContentLayout(R.layout.activity_settings)
class SettingsActivity : BaseActivity() {

    @BindView(R.id.buttonDefaultProfile) lateinit var buttonDefaultProfile: SettingsItem
    @BindView(R.id.switchAutoStart) lateinit var switchAutoStart: SettingsSwitch
    @BindView(R.id.buttonMtuSize) lateinit var buttonMtuSize: SettingsItem
    @BindView(R.id.switchShowIcon) lateinit var switchShowIcon: SettingsSwitch
    @BindView(R.id.switchDnsLeak) lateinit var switchDnsLeak: SettingsSwitch
    @BindView(R.id.switchBypassLocal) lateinit var switchBypassLocal: SettingsSwitch
    @BindView(R.id.switchShowSplitTunnel) lateinit var switchShowSplitTunnel: SettingsSwitch
    @BindView(R.id.switchDnsOverHttps) lateinit var switchDnsOverHttps: SettingsSwitch
    @BindView(R.id.buttonProtocol) lateinit var buttonProtocol: SettingsItem
    @BindView(R.id.splitTunnelLayout) lateinit var splitTunnelLayout: View
    @BindView(R.id.scrollView) lateinit var scrollView: NestedScrollView
    @BindView(R.id.buttonExcludeIps) lateinit var buttonExcludeIps: SettingsItem
    @BindView(R.id.buttonExcludeApps) lateinit var buttonExcludeApps: SettingsItem
    @BindView(R.id.buttonAlwaysOn) lateinit var buttonAlwaysOn: SettingsItem
    @BindView(R.id.buttonLicenses) lateinit var buttonLicenses: SettingsItem
    @BindView(R.id.netShieldSwitch) lateinit var switchNetShield: NetShieldSwitch
    @BindView(R.id.switchVpnAccelerator) lateinit var switchVpnAccelerator: SettingsSwitch
    @BindView(R.id.switchVpnAcceleratorNotifications) lateinit var switchVpnAcceleratorNotifications: SettingsSwitch
    @Inject lateinit var serverManager: ServerManager
    @Inject lateinit var stateMonitor: VpnStateMonitor
    @Inject lateinit var connectionManager: VpnConnectionManager
    @Inject lateinit var userPrefs: UserData
    @Inject lateinit var appConfig: AppConfig
    @Inject lateinit var installedAppsProvider: InstalledAppsProvider

    private var loadExcludedAppsJob: Job? = null

    private val protocolSelection =
        registerForActivityResult(ProtocolSelectionActivity.createContract()) {
            if (it != null) {
                userPrefs.setProtocols(it.protocol, (it as? ProtocolSelection.OpenVPN)?.transmission)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initToolbarWithUpEnabled()
        initSettings()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initSettings() {
        initOSRelatedVisibility()
        buttonAlwaysOn.setOnClickListener { navigateTo(AlwaysOnSettingsActivity::class.java); }
        switchAutoStart.isChecked = userPrefs.connectOnBoot
        switchAutoStart.setOnCheckedChangeListener { _, isChecked ->
            userPrefs.connectOnBoot = isChecked
        }
        switchNetShield.init(userPrefs.netShieldProtocol, appConfig, this, userPrefs, stateMonitor, connectionManager) {
            userPrefs.netShieldProtocol = it
        }
        switchShowIcon.isChecked = userPrefs.shouldShowIcon()
        switchShowIcon.setOnCheckedChangeListener { _, isChecked ->
            userPrefs.setShowIcon(isChecked)
            EventBus.getInstance().post(StatusSettingChanged(isChecked))
        }

        switchDnsLeak.isEnabled = false

        switchDnsOverHttps.setInfoText(R.string.settingsAllowAlternativeRoutingDescription)
        switchDnsOverHttps.isChecked = userPrefs.apiUseDoH
        switchDnsOverHttps.setOnCheckedChangeListener { _, isChecked ->
            userPrefs.apiUseDoH = isChecked
        }

        buttonDefaultProfile.setOnClickListener {
            navigateTo(SettingsDefaultProfileActivity::class.java)
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
        buttonMtuSize.setOnTouchListener(disableWhenConnectedListener)
        buttonMtuSize.setOnClickListener { navigateTo(SettingsMtuActivity::class.java) }

        buttonProtocol.setOnTouchListener(disableWhenConnectedListener)
        buttonProtocol.setOnClickListener {
            protocolSelection.launch(getProtocolSelection(userPrefs))
        }

        initSplitTunneling(useSplitTunnel)
        switchShowSplitTunnel.setOnTouchListener(disableWhenConnectedListener)
        buttonExcludeIps.setOnTouchListener(disableWhenConnectedListener)
        buttonExcludeIps.setOnClickListener {
            navigateTo(SettingsExcludeIpsActivity::class.java)
        }
        buttonExcludeApps.setOnTouchListener(disableWhenConnectedListener)
        buttonExcludeApps.setOnClickListener {
            navigateTo(SettingsExcludeAppsActivity::class.java)
        }

        switchShowSplitTunnel.isChecked = useSplitTunnel
        switchShowSplitTunnel.setOnCheckedChangeListener { _, isChecked ->
            initSplitTunneling(isChecked)
            userPrefs.useSplitTunneling = isChecked
            scrollView.postDelayed({ scrollView.fullScroll(ScrollView.FOCUS_DOWN) }, 100)
        }

        switchBypassLocal.isChecked = userPrefs.bypassLocalTraffic()
        switchBypassLocal.setOnTouchListener(disableWhenConnectedListener)
        switchBypassLocal.setOnCheckedChangeListener{ _, isChecked ->
            userPrefs.setBypassLocalTraffic(isChecked)
        }

        initVpnAcceleratorToggles()

        buttonLicenses.setOnClickListener {
            navigateTo(OssLicensesActivity::class.java)
        }

        onUserDataUpdated()
        userPrefs.updateEvent.observe(this) {
            onUserDataUpdated()
        }
    }

    private fun initVpnAcceleratorToggles() {
        if (appConfig.getFeatureFlags().vpnAccelerator) {
            updateVpnAcceleratorToggles()
            switchVpnAccelerator.setInfoText(HtmlTools.fromHtml(getString(
                R.string.settingsVpnAcceleratorDescription, Constants.VPN_ACCELERATOR_INFO_URL)))
            switchVpnAccelerator.switchClickInterceptor = {
                tryToggleVpnAccelerator()
                true
            }
            userPrefs.updateEvent.observe(this) {
                updateVpnAcceleratorToggles()
            }

            switchVpnAcceleratorNotifications.isVisible = userPrefs.isVpnAcceleratorEnabled
            switchVpnAcceleratorNotifications.isChecked =
                userPrefs.showVpnAcceleratorNotifications()
            switchVpnAcceleratorNotifications.setOnCheckedChangeListener { _, isChecked ->
                userPrefs.setShowVpnAcceleratorNotifications(isChecked)
            }
        } else {
            switchVpnAccelerator.isVisible = false
            switchVpnAcceleratorNotifications.isVisible = false
        }
    }

    private fun initOSRelatedVisibility() {
        switchAutoStart.visibility = if (Build.VERSION.SDK_INT >= 24) GONE else VISIBLE
        buttonAlwaysOn.visibility = if (Build.VERSION.SDK_INT >= 24) VISIBLE else GONE
        switchShowIcon.visibility = if (Build.VERSION.SDK_INT >= 26) GONE else VISIBLE
    }

    private fun initSplitTunneling(isChecked: Boolean) {
        splitTunnelLayout.visibility = if (isChecked) VISIBLE else GONE
    }

    private fun updateVpnAcceleratorToggles() {
        val isEnabled = userPrefs.isVpnAcceleratorEnabled
        switchVpnAccelerator.isChecked = isEnabled
        switchVpnAcceleratorNotifications.isVisible = isEnabled
    }

    private fun onUserDataUpdated() {
        buttonDefaultProfile.setValue(serverManager.defaultConnection.name)
        buttonProtocol.setValue(getString(getProtocolSelection(userPrefs).displayName))
        buttonExcludeIps.setValue(getListString(userPrefs.splitTunnelIpAddresses))
        buttonMtuSize.setValue(userPrefs.mtuSize.toString())

        loadExcludedAppsJob?.cancel()
        loadExcludedAppsJob = lifecycleScope.launch {
            val names = installedAppsProvider
                .getNamesOfInstalledApps(userPrefs.splitTunnelApps)
                .sortedByLocaleAware { it.toString() }
            buttonExcludeApps.setValue(getListString(names))
        }
    }

    // Possible improvement: measure if the text fits and adjust the number of listed items to
    // avoid ellipsis.
    private fun getListString(items: List<CharSequence>): CharSequence {
        val maxListedCount = 2
        val count = items.size
        return when {
            count == 0 -> getString(R.string.listNoItems)
            count <= maxListedCount -> items.joinToString(", ")
            else -> resources.getQuantityString(
                R.plurals.listFewItemsAndMore,
                items.size - maxListedCount,
                items.take(maxListedCount).joinToString(", "),
                items.size - maxListedCount
            )
        }
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

    private fun getProtocolSelection(userData: UserData) =
        ProtocolSelection.from(userData.selectedProtocol, userData.transmissionProtocol)

}
