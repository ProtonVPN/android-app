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

import android.animation.LayoutTransition
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import androidx.activity.result.ActivityResultCallback
import androidx.annotation.ColorInt
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.core.view.isVisible
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.color.MaterialColors
import com.protonvpn.android.R
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.components.BaseActivityV2
import com.protonvpn.android.components.InstalledAppsProvider
import com.protonvpn.android.databinding.ActivitySettingsBinding
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.logging.UiReconnect
import com.protonvpn.android.logging.logUiSettingChange
import com.protonvpn.android.logging.Setting
import com.protonvpn.android.netshield.NetShieldProtocol
import com.protonvpn.android.netshield.NetShieldSwitch
import com.protonvpn.android.netshield.getNetShieldAvailability
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.ui.ProtocolSelectionActivity
import com.protonvpn.android.ui.planupgrade.UpgradeModerateNatDialogActivity
import com.protonvpn.android.ui.planupgrade.UpgradeSafeModeDialogActivity
import com.protonvpn.android.ui.showGenericReconnectDialog
import com.protonvpn.android.userstorage.ProfileManager
import com.protonvpn.android.utils.BuildConfigUtils
import com.protonvpn.android.utils.ColorUtils.combineArgb
import com.protonvpn.android.utils.ColorUtils.mixDstOver
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.HtmlTools
import com.protonvpn.android.utils.ViewUtils.viewBinding
import com.protonvpn.android.utils.scrollToShowView
import com.protonvpn.android.utils.sortedByLocaleAware
import com.protonvpn.android.vpn.ProtocolSelection
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnStatusProviderUI
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val PREF_SHOW_VPN_ACCELERATOR_RECONNECT_DLG = "PREF_SHOW_VPN_ACCELERATOR_RECONNECT_DIALOG"
private const val PREF_SHOW_SPLIT_TUNNELING_RECONNECT_DLG = "PREF_SHOW_SPLIT_TUNNELING_RECONNECT_DIALOG"
private const val PREF_SHOW_BYPASS_LOCAL_RECONNECT_DIALOG = "PREF_SHOW_BYPASS_LOCAL_RECONNECT_DIALOG"
private const val PREF_SHOW_EXCLUDED_IPS_RECONNECT_DIALOG = "PREF_SHOW_EXCLUDED_IPS_RECONNECT_DIALOG"
private const val PREF_SHOW_EXCLUDED_APPS_RECONNECT_DIALOG = "PREF_SHOW_EXCLUDED_APPS_RECONNECT_DIALOG"
private const val PREF_SHOW_PROTOCOL_RECONNECT_DIALOG = "PREF_SHOW_PROTOCOL_RECONNECT_DIALOG"
private const val PREF_SHOW_MTU_SIZE_RECONNECT_DIALOG = "PREF_SHOW_MTU_SIZE_RECONNECT_DIALOG"
private const val PREF_SHOW_NAT_MODE_RECONNECT_DIALOG = "PREF_SHOW_NAT_MODE_RECONNECT_DIALOG"
private const val PREF_SHOW_SAFE_MODE_RECONNECT_DIALOG = "PREF_SHOW_SAFE_MODE_RECONNECT_DIALOG"

@AndroidEntryPoint
class SettingsActivity : BaseActivityV2() {

    @Inject lateinit var vpnStatusProviderUI: VpnStatusProviderUI
    @Inject lateinit var connectionManager: VpnConnectionManager
    @Inject lateinit var userSettingsManager: CurrentUserLocalSettingsManager
    @Inject lateinit var appConfig: AppConfig
    @Inject lateinit var installedAppsProvider: InstalledAppsProvider
    @Inject lateinit var currentUser: CurrentUser
    @Inject lateinit var profileManager: ProfileManager
    @Inject lateinit var buildConfigInfo: BuildConfigInfo

    private val binding by viewBinding(ActivitySettingsBinding::inflate)
    private var loadExcludedAppsJob: Job? = null
    private var currentProtocolCached: ProtocolSelection = ProtocolSelection.SMART

    private val protocolSelection =
        registerForActivityResult(ProtocolSelectionActivity.createContract()) { protocol ->
            if (protocol != null) {
                val settingsUpdated = currentProtocolCached != protocol
                logUiEvent(Setting.DEFAULT_PROTOCOL)
                lifecycleScope.launch {
                    userSettingsManager.updateProtocol(protocol)
                }
                if (settingsUpdated && vpnStatusProviderUI.connectionProfile?.hasCustomProtocol() == false) {
                    onConnectionSettingsChanged(PREF_SHOW_PROTOCOL_RECONNECT_DIALOG)
                }
            }
        }

    private val excludedAppsSettings = registerForActivityResult(
        SettingsExcludeAppsActivity.createContract(),
        createConnectionSettingResultHandler(PREF_SHOW_EXCLUDED_APPS_RECONNECT_DIALOG)
    )
    private val excludeIpsSettings = registerForActivityResult(
        SettingsExcludeIpsActivity.createContract(),
        createConnectionSettingResultHandler(PREF_SHOW_EXCLUDED_IPS_RECONNECT_DIALOG)
    )
    private val mtuSizeSettings = registerForActivityResult(
        SettingsMtuActivity.createContract(),
        createConnectionSettingResultHandler(PREF_SHOW_MTU_SIZE_RECONNECT_DIALOG)
    )

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
        initDohToggle()
        initVpnAcceleratorToggles()

        with(binding.contentSettings) {
            val flags = appConfig.getFeatureFlags()
            switchNonStandardPorts.isVisible = flags.safeMode

            if (BuildConfigUtils.displayInfo()) {
                buildConfigInfo.isVisible = true
                buildConfigInfo.setValue(buildConfigInfo())
            }
        }

        var isFirstUpdate = true
        val user = lifecycleScope.async { currentUser.vpnUserFlow.firstOrNull() }
        userSettingsManager.rawCurrentUserSettingsFlow
            .flowWithLifecycle(lifecycle)
            .onEach { settings ->
                onUserDataUpdated(settings)
                if (isFirstUpdate) {
                    // Set listeners after initial values have been set, otherwise they will be triggered.
                    setListeners(user.await(), settings)
                    binding.contentSettings.root.jumpDrawablesToCurrentState()
                    onUiReady()
                    isFirstUpdate = false
                }
                currentProtocolCached = settings.protocol
            }
            .launchIn(lifecycleScope)
    }

    private fun setListeners(user: VpnUser?, settings: LocalUserSettings) {
        with(binding.contentSettings) {
            buttonAlwaysOn.setOnClickListener { navigateTo(SettingsAlwaysOnActivity::class.java); }

            switchAutoStart.setOnCheckedChangeListener { _, isChecked ->
                logUiEvent(Setting.CONNECT_ON_BOOT)
                lifecycleScope.launch {
                    userSettingsManager.updateConnectOnBoot(isChecked)
                }
            }

            buttonDefaultProfile.setOnClickListener {
                navigateTo(SettingsDefaultProfileActivity::class.java)
            }

            buttonMtuSize.setOnClickListener { mtuSizeSettings.launch(Unit) }

            buttonProtocol.setOnClickListener {
                protocolSelection.launch(currentProtocolCached)
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
            switchVpnAccelerator.switchClickInterceptor = {
                tryToggleVpnAccelerator()
                true
            }

            buttonTelemetry.setOnClickListener { navigateTo(SettingsTelemetryActivity::class.java) }
            buttonLicenses.setOnClickListener { navigateTo(OssLicensesActivity::class.java) }
            initNonStandardPortsToggle(user)
            initModerateNatToggle(user)
            initNetShield(settings.netShield, user)
        }
    }

    private fun onUiReady() {
        // Enable layout animations only after all UI elements have their state set, including those
        // that require an async operation to get their state.
        // For some reason in UI tests the UI scrolls down when animations are enabled and
        // switchNonStandardPorts is set to visible, which breaks the tests.
        binding.contentSettings.scrollView.layoutTransition = LayoutTransition()
    }

    private fun initDohToggle() = with(binding.contentSettings) {
        val info =
            getString(R.string.settingsAllowAlternativeRoutingDescription, Constants.ALTERNATIVE_ROUTING_LEARN_URL)
        switchDnsOverHttps.setInfoText(HtmlTools.fromHtml(info), hasLinks = true)
        switchDnsOverHttps.setOnCheckedChangeListener { _, isChecked ->
            logUiEvent(Setting.API_DOH)
            lifecycleScope.launch {
                userSettingsManager.updateApiUseDoh(isChecked)
            }
        }
    }

    private fun initVpnAcceleratorToggles() = with(binding.contentSettings) {
        if (appConfig.getFeatureFlags().vpnAccelerator) {
            val info =
                getString(R.string.settingsVpnAcceleratorDescription, Constants.VPN_ACCELERATOR_INFO_URL)
            switchVpnAccelerator.setInfoText(HtmlTools.fromHtml(info), hasLinks = true)
            switchVpnAccelerator.switchClickInterceptor = {
                tryToggleVpnAccelerator()
                true
            }
        } else {
            switchVpnAccelerator.isVisible = false
            switchVpnAcceleratorNotifications.isVisible = false
        }
    }

    private fun initModerateNatToggle(user: VpnUser?) = with(binding.contentSettings) {
        val info = getString(R.string.settingsModerateNatDescription, Constants.MODERATE_NAT_INFO_URL)
        switchModerateNat.setInfoText(HtmlTools.fromHtml(info), hasLinks = true)
        if (user?.isUserBasicOrAbove == true) {
            switchModerateNat.switchClickInterceptor = {
                tryToggleNatMode()
                true
            }
        } else {
            switchModerateNat.switchClickInterceptor = {
                navigateTo(UpgradeModerateNatDialogActivity::class.java)
                true
            }
        }
    }

    private fun initNonStandardPortsToggle(user: VpnUser?) = with(binding.contentSettings) {
        val flags = appConfig.getFeatureFlags()
        if (flags.safeMode) {
            val info = getString(R.string.settingsAllowNonStandardPortsDescription, Constants.SAFE_MODE_INFO_URL)
            switchNonStandardPorts.setInfoText(HtmlTools.fromHtml(info), hasLinks = true)
            if (user?.isUserBasicOrAbove == true) {
                switchNonStandardPorts.switchClickInterceptor = {
                    tryToggleSafeMode()
                    true
                }
            } else {
                switchNonStandardPorts.switchClickInterceptor = {
                    navigateTo(UpgradeSafeModeDialogActivity::class.java)
                    true
                }
            }
        }
    }

    private fun initNetShield(initialValue: NetShieldProtocol, user: VpnUser?) = with(binding.contentSettings) {
        netShieldSwitch.init(
            initialValue,
            appConfig,
            this@SettingsActivity,
            user.getNetShieldAvailability(),
            NetShieldSwitch.ReconnectDialogDelegate(
                getVpnUiDelegate(),
                vpnStatusProviderUI,
                connectionManager
            )
        ) {
            logUiEvent(Setting.NETSHIELD_PROTOCOL)
            lifecycleScope.launch {
                userSettingsManager.updateNetShield(it)
            }
        }
    }

    private fun initOSRelatedVisibility() = with(binding.contentSettings) {
        switchAutoStart.visibility = if (Build.VERSION.SDK_INT >= 24) GONE else VISIBLE
        buttonAlwaysOn.visibility = if (Build.VERSION.SDK_INT >= 24) VISIBLE else GONE
    }

    private fun initDnsLeakProtection() {
        with(binding.contentSettings.switchDnsLeak.switchView) {
            thumbTintList = ColorStateList.valueOf(alwaysOnThumbColor(this))
        }
    }

    private fun onUserDataUpdated(localUserSettings: LocalUserSettings) = with(binding.contentSettings) {
        switchDnsOverHttps.isChecked = localUserSettings.apiUseDoh
        switchAutoStart.isChecked = localUserSettings.connectOnBoot
        switchShowSplitTunnel.isChecked = localUserSettings.splitTunneling.isEnabled
        splitTunnelLayout.visibility = if (switchShowSplitTunnel.isChecked) VISIBLE else GONE
        switchBypassLocal.isChecked = localUserSettings.lanConnections
        switchNonStandardPorts.isChecked = localUserSettings.safeMode != true
        switchModerateNat.isChecked = !localUserSettings.randomizedNat
        if (appConfig.getFeatureFlags().vpnAccelerator) {
            switchVpnAcceleratorNotifications.isVisible = localUserSettings.vpnAccelerator
            switchVpnAccelerator.isChecked = localUserSettings.vpnAccelerator

            switchVpnAcceleratorNotifications.isChecked = localUserSettings.vpnAcceleratorNotifications
            switchVpnAcceleratorNotifications.setOnCheckedChangeListener { _, isChecked ->
                lifecycleScope.launch {
                    logUiEvent(Setting.VPN_ACCELERATOR_NOTIFICATIONS)
                    userSettingsManager.update { it.copy(vpnAcceleratorNotifications = isChecked) }
                }
            }
        }

        // Pass the localUserSettings.defaultProfileId explicitly, otherwise ProfileManager uses its cached value of
        // settings and may return old value.
        val defaultProfile =
            profileManager.findProfile(localUserSettings.defaultProfileId) ?: profileManager.fallbackProfile
        buttonDefaultProfile.setValue(defaultProfile.getDisplayName(this@SettingsActivity))
        buttonProtocol.setValue(getString(localUserSettings.protocol.displayName))
        buttonExcludeIps.setValue(getListString(localUserSettings.splitTunneling.excludedIps))
        buttonMtuSize.setValue(localUserSettings.mtuSize.toString())

        loadExcludedAppsJob?.cancel()
        loadExcludedAppsJob = lifecycleScope.launch {
            val names = installedAppsProvider
                .getNamesOfInstalledApps(localUserSettings.splitTunneling.excludedApps)
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
            PREF_SHOW_VPN_ACCELERATOR_RECONNECT_DLG,
            "VPN Accelerator toggle",
            vpnStatusProviderUI.connectionProtocol?.localAgentEnabled() != true
        ) {
            lifecycleScope.launch {
                logUiEvent(Setting.VPN_ACCELERATOR_ENABLED)
                userSettingsManager.update { it.copy(vpnAccelerator = !it.vpnAccelerator) }
            }
        }
    }

    private fun tryToggleSplitTunneling() {
        tryToggleSwitch(
            PREF_SHOW_SPLIT_TUNNELING_RECONNECT_DLG,
            "split tunneling toggle",
        ) {
            logUiEvent(Setting.SPLIT_TUNNEL_ENABLED)
            lifecycleScope.launch {
                userSettingsManager.toggleSplitTunnelingEnabled()
                delay(100)
                with(binding.contentSettings) {
                    if (switchShowSplitTunnel.isChecked) {
                        scrollView.scrollToShowView(splitTunnelLayout)
                    }
                }
            }
        }
    }

    private fun tryToggleBypassLocal() {
        tryToggleSwitch(
            PREF_SHOW_BYPASS_LOCAL_RECONNECT_DIALOG,
            "LAN connections toggle"
        ) {
            logUiEvent(Setting.LAN_CONNECTIONS)
            lifecycleScope.launch {
                userSettingsManager.update { it.copy(lanConnections = !it.lanConnections) }
            }
        }
    }

    private fun tryToggleNatMode() {
        tryToggleSwitch(
            PREF_SHOW_NAT_MODE_RECONNECT_DIALOG,
            "Moderate NAT toggle",
            vpnStatusProviderUI.connectionProtocol?.localAgentEnabled() != true
        ) {
            logUiEvent(Setting.RESTRICTED_NAT)
            lifecycleScope.launch {
                userSettingsManager.update { it.copy(randomizedNat = !it.randomizedNat)}
            }
        }
    }

    private fun tryToggleSafeMode() {
        tryToggleSwitch(
            PREF_SHOW_SAFE_MODE_RECONNECT_DIALOG,
            "safe mode toggle",
            vpnStatusProviderUI.connectionProtocol?.localAgentEnabled() != true
        ) {
            logUiEvent(Setting.SAFE_MODE)
            lifecycleScope.launch {
                userSettingsManager.toggleSafeMode()
            }
        }
    }

    private fun tryToggleSwitch(
        showDialogPrefsKey: String,
        uiElement: String,
        needsReconnectIfConnected: Boolean = true,
        toggle: () -> Unit
    ) {
        if (needsReconnectIfConnected && vpnStatusProviderUI.isEstablishingOrConnected) {
            showGenericReconnectDialog(this, R.string.settingsReconnectToChangeDialogContent, showDialogPrefsKey) {
                toggle()
                ProtonLogger.log(UiReconnect, uiElement)
                connectionManager.reconnectWithCurrentParams(getVpnUiDelegate())
            }
        } else {
            toggle()
        }
    }

    private fun onConnectionSettingsChanged(showReconnectDialogPrefKey: String) {
        if (vpnStatusProviderUI.isEstablishingOrConnected) {
            showGenericReconnectDialog(
                this,
                R.string.settingsReconnectToApplySettingsDialogContent,
                showReconnectDialogPrefKey,
                R.string.reconnect_now
            ) {
                ProtonLogger.log(UiReconnect, "apply new settings")
                connectionManager.reconnect("user via settings change", getVpnUiDelegate())
            }
        }
    }

    private fun createConnectionSettingResultHandler(showReconnectDialogPrefKey: String) =
        ActivityResultCallback<Boolean?> { settingsUpdated ->
            if (settingsUpdated == true) {
                onConnectionSettingsChanged(showReconnectDialogPrefKey)
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

    private fun logUiEvent(setting: Setting) {
        ProtonLogger.logUiSettingChange(setting, "settings screen")
    }
}
