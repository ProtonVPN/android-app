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
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ScrollView
import androidx.core.widget.NestedScrollView
import br.com.sapereaude.maskedEditText.MaskedEditText
import butterknife.BindView
import com.google.android.material.snackbar.Snackbar
import com.protonvpn.android.R
import com.protonvpn.android.bus.EventBus
import com.protonvpn.android.bus.StatusSettingChanged
import com.protonvpn.android.components.BaseActivity
import com.protonvpn.android.components.ContentLayout
import com.protonvpn.android.components.EditTextValidator
import com.protonvpn.android.components.Listable
import com.protonvpn.android.components.ProtonSpinner
import com.protonvpn.android.components.ProtonSwitch
import com.protonvpn.android.components.SplitTunnelButton
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.vpn.VpnStateMonitor
import javax.inject.Inject

@ContentLayout(R.layout.activity_settings)
class SettingsActivity : BaseActivity() {

    @BindView(R.id.spinnerDefaultProtocol)
    lateinit var spinnerDefaultProtocol: ProtonSpinner<MockUDP>
    @BindView(R.id.spinnerDefaultConnection)
    lateinit var spinnerDefaultConnection: ProtonSpinner<Profile>
    @BindView(R.id.spinnerTransmissionProtocol)
    lateinit var spinnerTransmissionProtocol: ProtonSpinner<MockUDP>
    @BindView(R.id.switchAutoStart) lateinit var switchAutoStart: ProtonSwitch
    @BindView(R.id.textMTU) lateinit var textMTU: MaskedEditText
    @BindView(R.id.switchShowIcon) lateinit var switchShowIcon: ProtonSwitch
    @BindView(R.id.switchBypassLocal) lateinit var switchBypassLocal: ProtonSwitch
    @BindView(R.id.switchShowSplitTunnel) lateinit var switchShowSplitTunnel: ProtonSwitch
    @BindView(R.id.splitTunnelLayout) lateinit var splitTunnelLayout: View
    @BindView(R.id.scrollView) lateinit var scrollView: NestedScrollView
    @BindView(R.id.splitTunnelIPs) lateinit var splitTunnelIPs: SplitTunnelButton
    @BindView(R.id.splitTunnelApps) lateinit var splitTunnelApps: SplitTunnelButton
    @BindView(R.id.buttonAlwaysOn) lateinit var buttonAlwaysOn: ProtonSwitch
    @BindView(R.id.buttonLicenses) lateinit var buttonLicenses: ProtonSwitch
    @BindView(R.id.layoutTransmissionProtocol) lateinit var layoutTransmissionProtocol: View
    @Inject lateinit var serverManager: ServerManager
    @Inject lateinit var stateMonitor: VpnStateMonitor
    @Inject lateinit var userPrefs: UserData

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
        splitTunnelIPs.buttonManage.contentDescription = "Exclude IP addresses"
        buttonAlwaysOn.setOnClickListener { startActivity(Intent("android.net.vpn.SETTINGS")); }
        switchAutoStart.switchProton.isChecked = userPrefs.connectOnBoot
        switchAutoStart.switchProton
                .setOnCheckedChangeListener { _, isChecked ->
                    userPrefs.connectOnBoot = isChecked
                }

        switchShowIcon.switchProton.isChecked = userPrefs.shouldShowIcon()
        switchShowIcon.switchProton
                .setOnCheckedChangeListener { _, isChecked ->
                    userPrefs.setShowIcon(isChecked)
                    EventBus.getInstance().post(StatusSettingChanged(isChecked))
                }

        initTransmissionProtocol()
        spinnerDefaultConnection.setItems(serverManager.savedProfiles)
        spinnerDefaultConnection.selectedItem = serverManager.defaultConnection
        spinnerDefaultConnection.setOnItemSelectedListener { item, _ ->
            userPrefs.defaultConnection = item
        }

        var snackBar: Snackbar? = null
        val useSplitTunnel = userPrefs.useSplitTunneling
        val touchListener = { _: View, _: MotionEvent ->
            if (stateMonitor.isConnected) {
                // Creating snackbar without showing it might lead to leaking activity.
                // Fixed in 1.1.0-alpha of material library.
                if (snackBar == null) {
                    snackBar =
                            Snackbar.make(findViewById(R.id.coordinator), "Cannot change this while connected", Snackbar.LENGTH_LONG)
                }
                if (snackBar?.isShownOrQueued == false) {
                    snackBar?.show()
                }
                true
            } else false
        }
        textMTU.setOnTouchListener(touchListener)
        spinnerDefaultProtocol.setOnTouchListener(touchListener)
        spinnerTransmissionProtocol.setOnTouchListener(touchListener)

        initSplitTunneling(useSplitTunnel)
        switchShowSplitTunnel.switchProton.contentDescription =
                getString(R.string.splitTunnellingSwitch)
        switchShowSplitTunnel.switchProton.setOnTouchListener(touchListener)
        splitTunnelApps.buttonManage.setOnTouchListener(touchListener)
        splitTunnelIPs.buttonManage.setOnTouchListener(touchListener)
        switchShowSplitTunnel.switchProton.isChecked = useSplitTunnel
        switchShowSplitTunnel.switchProton.setOnCheckedChangeListener { _, isChecked ->
            initSplitTunneling(isChecked)
            userPrefs.useSplitTunneling = isChecked
            scrollView.postDelayed({ scrollView.fullScroll(ScrollView.FOCUS_DOWN) }, 100)
        }

        switchBypassLocal.switchProton.isChecked = userPrefs.bypassLocalTraffic()
        switchBypassLocal.switchProton.setOnTouchListener(touchListener)
        switchBypassLocal.switchProton.setOnCheckedChangeListener { _, isChecked ->
            userPrefs.setBypassLocalTraffic(isChecked)
        }

        buttonLicenses.setOnClickListener {
            navigateTo(OssLicensesActivity::class.java)
        }
    }

    private fun initTransmissionProtocol() {
        spinnerDefaultProtocol.selectedItem = MockUDP(userPrefs.selectedProtocol.toString())
        spinnerDefaultProtocol.setItems(listOf(MockUDP(VpnProtocol.IKEv2.toString()), MockUDP(VpnProtocol.OpenVPN.toString())))
        spinnerDefaultProtocol.setOnItemSelectedListener { item, _ ->
            userPrefs.selectedProtocol = VpnProtocol.valueOf(item.label)
            initTransmissionProtocol()
            initSplitTunneling(userPrefs.useSplitTunneling)
        }
        val visibility = if (userPrefs.selectedProtocol == VpnProtocol.OpenVPN) VISIBLE else GONE
        layoutTransmissionProtocol.visibility = visibility
        switchBypassLocal.visibility = visibility
        spinnerTransmissionProtocol.selectedItem = MockUDP(userPrefs.transmissionProtocol)
        spinnerTransmissionProtocol.setItems(listOf(MockUDP(TransmissionProtocol.TCP.toString()), MockUDP(TransmissionProtocol.UDP.toString())))
        spinnerTransmissionProtocol.setOnItemSelectedListener { item, _ ->
            userPrefs.setTransmissionProtocol(TransmissionProtocol.valueOf(item.label))
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
                hideSoftKeyBoard()
                true
            } else {
                false
            }
        }
    }

    private fun initOSRelatedVisibility() {
        switchAutoStart.visibility = if (Build.VERSION.SDK_INT >= 26) GONE else VISIBLE
        buttonAlwaysOn.visibility = if (Build.VERSION.SDK_INT >= 26) VISIBLE else GONE
        switchShowIcon.visibility = if (Build.VERSION.SDK_INT >= 26) GONE else VISIBLE
    }

    private fun initSplitTunneling(isChecked: Boolean) {
        splitTunnelIPs.visibility = if (userPrefs.isOpenVPNSelected) GONE else VISIBLE
        splitTunnelLayout.visibility = if (isChecked) VISIBLE else GONE
        switchShowSplitTunnel.setDividerVisibility(if (isChecked) GONE else VISIBLE)
    }

    class MockUDP(private val name: String) : Listable {

        override fun getLabel(): String {
            return name
        }
    }
}
