/*
 * Copyright (c) 2023 Proton AG
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
import android.os.Bundle
import android.view.View
import androidx.activity.result.ActivityResultCallback
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.color.MaterialColors
import com.protonvpn.android.R
import com.protonvpn.android.appconfig.Restrictions
import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.components.VpnUiDelegateProvider
import com.protonvpn.android.databinding.ContentSettingsBinding
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.logging.Setting
import com.protonvpn.android.logging.UiReconnect
import com.protonvpn.android.logging.logUiSettingChange
import com.protonvpn.android.netshield.NetShieldProtocol
import com.protonvpn.android.netshield.NetShieldSwitch
import com.protonvpn.android.netshield.getNetShieldAvailability
import com.protonvpn.android.redesign.settings.ui.SettingsViewModel
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.ui.ProtocolSelectionActivity
import com.protonvpn.android.ui.drawer.LogActivity
import com.protonvpn.android.ui.drawer.bugreport.DynamicReportActivity
import com.protonvpn.android.ui.planupgrade.UpgradeAllowLanHighlightsFragment
import com.protonvpn.android.ui.planupgrade.UpgradeDialogActivity
import com.protonvpn.android.ui.planupgrade.UpgradeModerateNatHighlightsFragment
import com.protonvpn.android.ui.planupgrade.UpgradeSafeModeHighlightsFragment
import com.protonvpn.android.ui.planupgrade.UpgradeSplitTunnelingHighlightsFragment
import com.protonvpn.android.ui.planupgrade.UpgradeVpnAcceleratorHighlightsFragment
import com.protonvpn.android.ui.showGenericReconnectDialog
import com.protonvpn.android.utils.AndroidUtils.launchActivity
import com.protonvpn.android.utils.BuildConfigUtils
import com.protonvpn.android.utils.ColorUtils
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.HtmlTools
import com.protonvpn.android.utils.openProtonUrl
import com.protonvpn.android.utils.scrollToShowView
import com.protonvpn.android.utils.sortedByLocaleAware
import com.protonvpn.android.vpn.ProtocolSelection
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import me.proton.core.presentation.utils.viewBinding
import me.proton.core.presentation.R as CoreR

private const val PREF_SHOW_VPN_ACCELERATOR_RECONNECT_DLG =
    "PREF_SHOW_VPN_ACCELERATOR_RECONNECT_DIALOG"
private const val PREF_SHOW_SPLIT_TUNNELING_RECONNECT_DLG =
    "PREF_SHOW_SPLIT_TUNNELING_RECONNECT_DIALOG"
private const val PREF_SHOW_BYPASS_LOCAL_RECONNECT_DIALOG =
    "PREF_SHOW_BYPASS_LOCAL_RECONNECT_DIALOG"
private const val PREF_SHOW_EXCLUDED_IPS_RECONNECT_DIALOG =
    "PREF_SHOW_EXCLUDED_IPS_RECONNECT_DIALOG"
private const val PREF_SHOW_EXCLUDED_APPS_RECONNECT_DIALOG =
    "PREF_SHOW_EXCLUDED_APPS_RECONNECT_DIALOG"
private const val PREF_SHOW_PROTOCOL_RECONNECT_DIALOG = "PREF_SHOW_PROTOCOL_RECONNECT_DIALOG"
private const val PREF_SHOW_MTU_SIZE_RECONNECT_DIALOG = "PREF_SHOW_MTU_SIZE_RECONNECT_DIALOG"
private const val PREF_SHOW_NAT_MODE_RECONNECT_DIALOG = "PREF_SHOW_NAT_MODE_RECONNECT_DIALOG"
private const val PREF_SHOW_SAFE_MODE_RECONNECT_DIALOG = "PREF_SHOW_SAFE_MODE_RECONNECT_DIALOG"

@AndroidEntryPoint
class SettingsFragment : Fragment(R.layout.content_settings) {

    private val viewModel: SettingsViewModel by viewModels()

    private val binding by viewBinding(ContentSettingsBinding::bind)
    private var loadExcludedAppsJob: Job? = null
    private var currentProtocolCached: ProtocolSelection = ProtocolSelection.SMART

    private val protocolSelection =
        registerForActivityResult(ProtocolSelectionActivity.createContract()) { protocol ->
            if (protocol != null) {
                val settingsUpdated = currentProtocolCached != protocol
                logUiEvent(Setting.DEFAULT_PROTOCOL)
                viewModel.updateProtocol(protocol)
                if (settingsUpdated && viewModel.vpnStatusProviderUI.isEstablishingOrConnected) {
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initSettings()
    }

    private fun createConnectionSettingResultHandler(showReconnectDialogPrefKey: String) =
        ActivityResultCallback<Boolean?> { settingsUpdated ->
            if (settingsUpdated == true) {
                onConnectionSettingsChanged(showReconnectDialogPrefKey)
            }
        }

    private fun onConnectionSettingsChanged(showReconnectDialogPrefKey: String) {
        if (viewModel.vpnStatusProviderUI.isEstablishingOrConnected) {
            showGenericReconnectDialog(
                requireContext(),
                R.string.settingsReconnectToApplySettingsDialogContent,
                showReconnectDialogPrefKey,
                R.string.reconnect_now
            ) {
                ProtonLogger.log(UiReconnect, "apply new settings")
                viewModel.connectionManager.reconnect(
                    "user via settings change",
                    getVpnUiDelegate()
                )
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initSettings() {
        initOSRelatedVisibility()
        initDnsLeakProtection()
        initDohToggle()
        initVpnAcceleratorToggles()
        initSupportSection()

        with(binding) {
            val flags = viewModel.appConfig.getFeatureFlags()
            switchNonStandardPorts.isVisible = flags.safeMode

            if (BuildConfigUtils.displayInfo()) {
                buildConfigInfo.isVisible = true
                buildConfigInfo.setValue(viewModel.buildConfigInfo())
            }
        }

        var previousTier: Int? = null
        combine(
            viewModel.userSettingsManager.rawCurrentUserSettingsFlow,
            viewModel.restrictionsConfig.restrictionFlow,
            viewModel.currentUser.vpnUserFlow.map { it?.userTier }
        ) { settings, restrictions, tier ->
            Triple(settings, restrictions, tier)
        }.flowWithLifecycle(lifecycle)
            .onEach { (settings, restrictions, tier) ->
                onUserDataUpdated(settings, restrictions)
                onRestrictionsUpdated(restrictions)
                if (previousTier != tier) {
                    // Set listeners after initial values have been set, otherwise they will be triggered.
                    setListeners(viewModel.currentUser.vpnUser(), settings)
                    binding.root.jumpDrawablesToCurrentState()
                    onUiReady()
                }
                previousTier = tier
                currentProtocolCached = settings.protocol
            }
            .launchIn(lifecycleScope)
    }

    private fun initSupportSection() = with(binding) {
        buttonHelp.setOnClickListener { requireContext().openProtonUrl(Constants.URL_SUPPORT) }
        buttonReportProblem.setOnClickListener { requireContext().launchActivity<DynamicReportActivity>() }
        buttonShowLog.setOnClickListener { requireContext().launchActivity<LogActivity>() }
    }

    private fun onUiReady() {
        // Enable layout animations only after all UI elements have their state set, including those
        // that require an async operation to get their state.
        // For some reason in UI tests the UI scrolls down when animations are enabled and
        // switchNonStandardPorts is set to visible, which breaks the tests.
        binding.scrollView.layoutTransition = LayoutTransition()
    }

    private fun initDohToggle() = with(binding) {
        val info =
            getString(
                R.string.settingsAllowAlternativeRoutingDescription,
                com.protonvpn.android.utils.Constants.ALTERNATIVE_ROUTING_LEARN_URL
            )
        switchDnsOverHttps.setInfoText(HtmlTools.fromHtml(info), hasLinks = true)
    }

    private fun initVpnAcceleratorToggles() = with(binding) {
        if (viewModel.appConfig.getFeatureFlags().vpnAccelerator) {
            val info =
                getString(
                    R.string.settingsVpnAcceleratorDescription,
                    com.protonvpn.android.utils.Constants.VPN_ACCELERATOR_INFO_URL
                )
            switchVpnAccelerator.setInfoText(HtmlTools.fromHtml(info), hasLinks = true)
        } else {
            switchVpnAccelerator.isVisible = false
        }
    }

    private fun initNonStandardPortsToggle(user: VpnUser?) = with(binding) {
        val flags = viewModel.appConfig.getFeatureFlags()
        if (flags.safeMode) {
            val info = getString(
                R.string.settingsAllowNonStandardPortsDescription,
                com.protonvpn.android.utils.Constants.SAFE_MODE_INFO_URL
            )
            switchNonStandardPorts.setInfoText(HtmlTools.fromHtml(info), hasLinks = true)
            if (user?.isUserBasicOrAbove == true) {
                switchNonStandardPorts.switchClickInterceptor = {
                    tryToggleSafeMode()
                    true
                }
            } else {
                switchNonStandardPorts.switchClickInterceptor = {
                    UpgradeDialogActivity.launch<UpgradeSafeModeHighlightsFragment>(requireContext())
                    true
                }
            }
        }
    }

    private fun initModerateNatToggle(user: VpnUser?) = with(binding) {
        val info = getString(
            R.string.settingsModerateNatDescription,
            com.protonvpn.android.utils.Constants.MODERATE_NAT_INFO_URL
        )
        val accessible = user?.isUserBasicOrAbove == true
        switchModerateNat.setInfoText(HtmlTools.fromHtml(info), hasLinks = true)
        switchModerateNat.setShowUpgrade(!accessible)
        if (accessible) {
            switchModerateNat.switchClickInterceptor = {
                tryToggleNatMode()
                true
            }
        } else {
            switchModerateNat.switchClickInterceptor = {
                UpgradeDialogActivity.launch<UpgradeModerateNatHighlightsFragment>(requireContext())
                true
            }
        }
    }

    private fun initNetShield(initialValue: NetShieldProtocol, user: VpnUser?) = with(binding) {
        netShieldSwitch.init(
            initialValue,
            viewModel.appConfig,
            viewLifecycleOwner,
            user.getNetShieldAvailability(),
            NetShieldSwitch.ReconnectDialogDelegate(
                getVpnUiDelegate(),
                viewModel.vpnStatusProviderUI,
                viewModel.connectionManager
            )
        ) {
            logUiEvent(Setting.NETSHIELD_PROTOCOL)
            lifecycleScope.launch {
                viewModel.userSettingsManager.updateNetShield(it)
            }
        }
    }

    private fun getVpnUiDelegate() = (requireActivity() as VpnUiDelegateProvider).getVpnUiDelegate()

    private fun initOSRelatedVisibility() = with(binding) {
        switchAutoStart.visibility =
            if (android.os.Build.VERSION.SDK_INT >= 24) View.GONE else View.VISIBLE
        buttonAlwaysOn.visibility =
            if (android.os.Build.VERSION.SDK_INT >= 24) View.VISIBLE else View.GONE
    }

    private fun initDnsLeakProtection() {
        with(binding.switchDnsLeak.switchView) {
            trackTintList = android.content.res.ColorStateList.valueOf(
                alwaysOnSwitchColor(
                    this,
                    CoreR.attr.brand_lighten_40
                )
            )
            thumbTintList = android.content.res.ColorStateList.valueOf(
                alwaysOnSwitchColor(
                    this,
                    CoreR.attr.brand_darken_20
                )
            )
        }
    }

    /**
     * Generate the always-on thumb or track color by painting its color with 50% opacity on top
     * of the background color.
     * Simply using alpha in the thumb color won't work because the track will show through - the
     * thumb needs to be fully opaque.
     */
    @Suppress("MagicNumber")
    @ColorInt
    private fun alwaysOnSwitchColor(view: View, @AttrRes colorAttr: Int): Int {
        val bg = MaterialColors.getColor(view, CoreR.attr.proton_background_norm)
        val color = MaterialColors.getColor(view, colorAttr)
        val alpha = 0x80
        return ColorUtils.combineArgb(
            0xff,
            ColorUtils.mixDstOver(bg.red, color.red, alpha),
            ColorUtils.mixDstOver(bg.green, color.green, alpha),
            ColorUtils.mixDstOver(bg.blue, color.blue, alpha)
        )
    }

    private fun onRestrictionsUpdated(restrictions: Restrictions) = with(binding) {
        switchVpnAccelerator.setShowUpgrade(restrictions.vpnAccelerator)
        switchBypassLocal.setShowUpgrade(restrictions.lan)
        switchShowSplitTunnel.setShowUpgrade(restrictions.splitTunneling)
    }

    private fun onUserDataUpdated(
        localUserSettings: LocalUserSettings,
        restrictions: Restrictions
    ) = with(binding) {
        switchDnsOverHttps.isChecked = localUserSettings.apiUseDoh
        switchAutoStart.isChecked = localUserSettings.connectOnBoot
        switchShowSplitTunnel.isChecked = localUserSettings.splitTunneling.isEnabled
        splitTunnelLayout.isVisible =
            switchShowSplitTunnel.isChecked && !restrictions.splitTunneling
        switchBypassLocal.isChecked = localUserSettings.lanConnections
        switchNonStandardPorts.isChecked = localUserSettings.safeMode != true
        switchModerateNat.isChecked = !localUserSettings.randomizedNat
        if (viewModel.appConfig.getFeatureFlags().vpnAccelerator) {
            switchVpnAccelerator.isChecked = localUserSettings.vpnAccelerator
        }

        buttonProtocol.setValue(getString(localUserSettings.protocol.displayName))
        buttonExcludeIps.setValue(getListString(localUserSettings.splitTunneling.excludedIps))
        buttonMtuSize.setValue(localUserSettings.mtuSize.toString())

        loadExcludedAppsJob?.cancel()
        loadExcludedAppsJob = lifecycleScope.launch {
            val names = viewModel.installedAppsProvider
                .getNamesOfInstalledApps(localUserSettings.splitTunneling.excludedApps)
                .sortedByLocaleAware { it.toString() }
            buttonExcludeApps.setValue(getListString(names))
        }
    }

    private fun setListeners(user: VpnUser?, settings: LocalUserSettings) {
        with(binding) {
            buttonAlwaysOn.setOnClickListener { requireContext().launchActivity<SettingsAlwaysOnActivity>() }

            switchDnsOverHttps.setOnCheckedChangeListener { _, isChecked ->
                logUiEvent(Setting.API_DOH)
                lifecycleScope.launch {
                    viewModel.userSettingsManager.updateApiUseDoh(isChecked)
                }
            }

            switchAutoStart.setOnCheckedChangeListener { _, isChecked ->
                logUiEvent(Setting.CONNECT_ON_BOOT)
                lifecycleScope.launch {
                    viewModel.userSettingsManager.updateConnectOnBoot(isChecked)
                }
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

            buttonTelemetry.setOnClickListener { requireContext().launchActivity<SettingsTelemetryActivity>() }
            buttonLicenses.setOnClickListener { requireContext().launchActivity<OssLicensesActivity>() }
            initNonStandardPortsToggle(user)
            initModerateNatToggle(user)
            initNetShield(settings.netShield, user)
        }
    }

    private fun logUiEvent(setting: Setting) {
        ProtonLogger.logUiSettingChange(setting, "settings screen")
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
        if (binding.switchVpnAccelerator.inUpgradeMode) {
            UpgradeDialogActivity.launch<UpgradeVpnAcceleratorHighlightsFragment>(requireContext())
        } else {
            tryToggleSwitch(
                PREF_SHOW_VPN_ACCELERATOR_RECONNECT_DLG,
                "VPN Accelerator toggle",
                viewModel.vpnStatusProviderUI.connectionProtocol?.localAgentEnabled() != true
            ) {
                lifecycleScope.launch {
                    logUiEvent(Setting.VPN_ACCELERATOR_ENABLED)
                    viewModel.userSettingsManager.update { it.copy(vpnAccelerator = !it.vpnAccelerator) }
                }
            }
        }
    }

    private fun tryToggleSplitTunneling() {
        if (binding.switchShowSplitTunnel.inUpgradeMode) {
            UpgradeDialogActivity.launch<UpgradeSplitTunnelingHighlightsFragment>(requireContext())
        } else {
            tryToggleSwitch(
                PREF_SHOW_SPLIT_TUNNELING_RECONNECT_DLG,
                "split tunneling toggle",
            ) {
                logUiEvent(Setting.SPLIT_TUNNEL_ENABLED)
                lifecycleScope.launch {
                    viewModel.userSettingsManager.toggleSplitTunnelingEnabled()
                    delay(100)
                    with(binding) {
                        if (switchShowSplitTunnel.isChecked) {
                            scrollView.scrollToShowView(splitTunnelLayout)
                        }
                    }
                }
            }
        }
    }

    private fun tryToggleBypassLocal() {
        if (binding.switchBypassLocal.inUpgradeMode) {
            UpgradeDialogActivity.launch<UpgradeAllowLanHighlightsFragment>(requireContext())
        } else {
            tryToggleSwitch(
                PREF_SHOW_BYPASS_LOCAL_RECONNECT_DIALOG,
                "LAN connections toggle"
            ) {
                logUiEvent(Setting.LAN_CONNECTIONS)
                lifecycleScope.launch {
                    viewModel.userSettingsManager.update { it.copy(lanConnections = !it.lanConnections) }
                }
            }
        }
    }

    private fun tryToggleNatMode() {
        tryToggleSwitch(
            PREF_SHOW_NAT_MODE_RECONNECT_DIALOG,
            "Moderate NAT toggle",
            viewModel.vpnStatusProviderUI.connectionProtocol?.localAgentEnabled() != true
        ) {
            logUiEvent(Setting.RESTRICTED_NAT)
            lifecycleScope.launch {
                viewModel.userSettingsManager.update { it.copy(randomizedNat = !it.randomizedNat) }
            }
        }
    }

    private fun tryToggleSafeMode() {
        tryToggleSwitch(
            PREF_SHOW_SAFE_MODE_RECONNECT_DIALOG,
            "safe mode toggle",
            viewModel.vpnStatusProviderUI.connectionProtocol?.localAgentEnabled() != true
        ) {
            logUiEvent(Setting.SAFE_MODE)
            lifecycleScope.launch {
                viewModel.userSettingsManager.toggleSafeMode()
            }
        }
    }

    private fun tryToggleSwitch(
        showDialogPrefsKey: String,
        uiElement: String,
        needsReconnectIfConnected: Boolean = true,
        toggle: () -> Unit
    ) {
        if (needsReconnectIfConnected && viewModel.vpnStatusProviderUI.isEstablishingOrConnected) {
            showGenericReconnectDialog(
                requireContext(),
                R.string.settingsReconnectToChangeDialogContent,
                showDialogPrefsKey
            ) {
                toggle()
                ProtonLogger.log(UiReconnect, uiElement)
                viewModel.connectionManager.reconnectWithCurrentParams(getVpnUiDelegate())
            }
        } else {
            toggle()
        }
    }

}
