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
package com.protonvpn.android.ui.settings

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.ScrollView
import androidx.activity.result.ActivityResultCallback
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.Theme
import com.google.android.material.color.MaterialColors
import com.protonvpn.android.R
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.bus.EventBus
import com.protonvpn.android.bus.StatusSettingChanged
import com.protonvpn.android.components.BaseActivityV2
import com.protonvpn.android.components.InstalledAppsProvider
import com.protonvpn.android.databinding.ActivitySettingsBinding
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.ui.ProtocolSelection
import com.protonvpn.android.ui.ProtocolSelectionActivity
import com.protonvpn.android.utils.ColorUtils.combineArgb
import com.protonvpn.android.utils.ColorUtils.mixDstOver
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.HtmlTools
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.ViewUtils.viewBinding
import com.protonvpn.android.utils.sortedByLocaleAware
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnStateMonitor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SettingsActivity : BaseActivityV2() {

    @Inject lateinit var serverManager: ServerManager
    @Inject lateinit var stateMonitor: VpnStateMonitor
    @Inject lateinit var connectionManager: VpnConnectionManager
    @Inject lateinit var userPrefs: UserData
    @Inject lateinit var appConfig: AppConfig
    @Inject lateinit var installedAppsProvider: InstalledAppsProvider

    private val binding by viewBinding(ActivitySettingsBinding::inflate)
    private var loadExcludedAppsJob: Job? = null

    private val protocolSelection =
        registerForActivityResult(ProtocolSelectionActivity.createContract()) {
            if (it != null) {
                val settingsUpdated = getProtocolSelection(userPrefs) != it
                userPrefs.setProtocols(it.protocol, (it as? ProtocolSelection.OpenVPN)?.transmission)
                if (settingsUpdated && stateMonitor.connectionProfile?.hasCustomProtocol() == false) {
                    onConnectionSettingsChanged()
                }
            }
        }
    private val connectionSettingResultHandler = ActivityResultCallback<Boolean?> { settingsUpdated ->
        if (settingsUpdated == true) {
            onConnectionSettingsChanged()
        }
    }
    private val excludedAppsSettings =
        registerForActivityResult(SettingsExcludeAppsActivity.createContract(), connectionSettingResultHandler)
    private val excludeIpsSettings =
        registerForActivityResult(SettingsExcludeIpsActivity.createContract(), connectionSettingResultHandler)
    private val mtuSizeSettings =
        registerForActivityResult(SettingsMtuActivity.createContract(), connectionSettingResultHandler)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        initToolbarWithUpEnabled(binding.contentAppbar.toolbar)
        initSettings()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initSettings() {
        initOSRelatedVisibility()
        initDnsLeakProtection()
        with(binding.contentSettings) {
            buttonAlwaysOn.setOnClickListener { navigateTo(SettingsAlwaysOnActivity::class.java); }
            switchAutoStart.isChecked = userPrefs.connectOnBoot
            switchAutoStart.setOnCheckedChangeListener { _, isChecked ->
                userPrefs.connectOnBoot = isChecked
            }
            netShieldSwitch.init(
                userPrefs.netShieldProtocol,
                appConfig,
                this@SettingsActivity,
                userPrefs,
                stateMonitor,
                connectionManager
            ) {
                userPrefs.netShieldProtocol = it
            }
            switchShowIcon.isChecked = userPrefs.shouldShowIcon()
            switchShowIcon.setOnCheckedChangeListener { _, isChecked ->
                userPrefs.setShowIcon(isChecked)
                EventBus.getInstance().post(StatusSettingChanged(isChecked))
            }

            switchDnsOverHttps.isChecked = userPrefs.apiUseDoH
            switchDnsOverHttps.setOnCheckedChangeListener { _, isChecked ->
                userPrefs.apiUseDoH = isChecked
            }

            buttonDefaultProfile.setOnClickListener {
                navigateTo(SettingsDefaultProfileActivity::class.java)
            }

            buttonMtuSize.setOnClickListener { mtuSizeSettings.launch(Unit) }

            buttonProtocol.setOnClickListener {
                protocolSelection.launch(getProtocolSelection(userPrefs))
            }

            buttonExcludeIps.setOnClickListener {
                excludeIpsSettings.launch(Unit)
            }
            buttonExcludeApps.setOnClickListener {
                excludedAppsSettings.launch(Unit)
            }
            switchShowSplitTunnel.switchClickInterceptor = {
                tryToggleSplitTunneling()
                true
            }

            switchBypassLocal.switchClickInterceptor = {
                tryToggleBypassLocal()
                true
            }

            initVpnAcceleratorToggles()

            buttonLicenses.setOnClickListener {
                navigateTo(OssLicensesActivity::class.java)
            }
        }

        onUserDataUpdated()
        userPrefs.updateEvent.observe(this) {
            onUserDataUpdated()
        }
    }

    private fun initVpnAcceleratorToggles() = with(binding.contentSettings) {
        if (appConfig.getFeatureFlags().vpnAccelerator) {
            updateVpnAcceleratorToggles()
            val info =
                getString(R.string.settingsVpnAcceleratorDescription, Constants.VPN_ACCELERATOR_INFO_URL)
            switchVpnAccelerator.setInfoText(HtmlTools.fromHtml(info), hasLinks = true)
            switchVpnAccelerator.switchClickInterceptor = {
                tryToggleVpnAccelerator()
                true
            }
            userPrefs.updateEvent.observe(this@SettingsActivity) {
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

    private fun initOSRelatedVisibility() = with(binding.contentSettings) {
        switchAutoStart.visibility = if (Build.VERSION.SDK_INT >= 24) GONE else VISIBLE
        buttonAlwaysOn.visibility = if (Build.VERSION.SDK_INT >= 24) VISIBLE else GONE
        switchShowIcon.visibility = if (Build.VERSION.SDK_INT >= 26) GONE else VISIBLE
    }

    private fun initDnsLeakProtection() {
        with(binding.contentSettings.switchDnsLeak.switchView) {
            thumbTintList = ColorStateList.valueOf(alwaysOnThumbColor(this))
        }
    }

    private fun updateVpnAcceleratorToggles() = with(binding.contentSettings) {
        val isEnabled = userPrefs.isVpnAcceleratorEnabled
        switchVpnAccelerator.isChecked = isEnabled
        switchVpnAcceleratorNotifications.isVisible = isEnabled
    }

    private fun onUserDataUpdated() = with(binding.contentSettings) {
        switchShowSplitTunnel.isChecked = userPrefs.useSplitTunneling
        splitTunnelLayout.visibility = if (switchShowSplitTunnel.isChecked) VISIBLE else GONE
        switchBypassLocal.isChecked = userPrefs.shouldBypassLocalTraffic()

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
        tryToggleSwitch(
            R.string.settingsSmartReconnectReconnectDialogContent,
            stateMonitor.connectionProtocol?.localAgentEnabled() != true
        ) {
            userPrefs.isVpnAcceleratorEnabled = !userPrefs.isVpnAcceleratorEnabled
        }
    }

    private fun tryToggleSplitTunneling() {
        tryToggleSwitch(
            R.string.settingsSplitTunnelReconnectDialogContent,
            !userPrefs.isSplitTunnelingConfigEmpty
        ) {
            userPrefs.useSplitTunneling = !userPrefs.useSplitTunneling
            with(binding.contentSettings) {
                if (switchShowSplitTunnel.isChecked) {
                    scrollView.postDelayed({ scrollView.fullScroll(ScrollView.FOCUS_DOWN) }, 100)
                }
            }
        }
    }

    private fun tryToggleBypassLocal() {
        tryToggleSwitch(R.string.settingsLanConnectionsReconnectDialogContent) {
            userPrefs.bypassLocalTraffic = !userPrefs.bypassLocalTraffic
        }
    }

    private fun tryToggleSwitch(
        @StringRes reconnectDialogText: Int,
        needsReconnectIfConnected: Boolean = true,
        toggle: () -> Unit
    ) {
        if (needsReconnectIfConnected && stateMonitor.isEstablishingOrConnected) {
            val builder = MaterialDialog.Builder(this).theme(Theme.DARK)
            builder
                .icon(ContextCompat.getDrawable(builder.context, R.drawable.ic_refresh)!!)
                .title(R.string.dialogTitleReconnectionNeeded)
                .content(reconnectDialogText)
                .positiveText(R.string.reconnect)
                .onPositive { _: MaterialDialog?, _: DialogAction? ->
                    toggle()
                    connectionManager.fullReconnect(this)
                }
                .negativeText(R.string.cancel)
                .show()
        } else {
            toggle()
        }
    }

    private fun getProtocolSelection(userData: UserData) =
        ProtocolSelection.from(userData.selectedProtocol, userData.transmissionProtocol)

    private fun onConnectionSettingsChanged() {
        if (stateMonitor.isEstablishingOrConnected) {
            MaterialDialog.Builder(this).theme(Theme.DARK)
                .title(R.string.dialogTitleReconnectionNeeded)
                .content(R.string.settingsReconnectToApplySettingsDialogContent)
                .positiveText(R.string.reconnect)
                .onPositive { _, _ -> connectionManager.fullReconnect(this) }
                .negativeText(R.string.cancel)
                .show()
        }
    }

    private fun navigateTo(clazz: Class<out Activity>) {
        startActivity(Intent(this, clazz))
    }

    /**
     * Generate the always-on thumb color by painting brand_darken_20 with 50% opacity on top
     * of the background color.
     * Simply using alpha in the thumb color won't work because the track will show through - the
     * thumb needs to be fully opaque.
     */
    @Suppress("MagicNumber")
    @ColorInt
    private fun alwaysOnThumbColor(view: View): Int {
        val bg = MaterialColors.getColor(view, R.attr.proton_background_norm)
        val thumb = MaterialColors.getColor(view, R.attr.brand_darken_20)
        val thumbAlpha = 0x80
        return combineArgb(
            0xff,
            mixDstOver(bg.red, thumb.red, thumbAlpha),
            mixDstOver(bg.green, thumb.green, thumbAlpha),
            mixDstOver(bg.blue, thumb.blue, thumbAlpha)
        )
    }
}
