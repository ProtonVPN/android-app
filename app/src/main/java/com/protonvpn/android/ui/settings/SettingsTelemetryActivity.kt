/*
 * Copyright (c) 2023. Proton AG
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

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.commitNow
import androidx.lifecycle.lifecycleScope
import com.protonvpn.android.R
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.components.BaseActivityV2
import com.protonvpn.android.databinding.ActivityTelemetrySettingsBinding
import com.protonvpn.android.databinding.FragmentTelemetrySettingsBinding
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.HtmlTools
import com.protonvpn.android.utils.SentryIntegration
import com.protonvpn.android.utils.ViewUtils.viewBinding
import com.zhuinden.fragmentviewbindingdelegatekt.viewBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SettingsTelemetryActivity : BaseActivityV2() {

    private val binding by viewBinding(ActivityTelemetrySettingsBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        initToolbarWithUpEnabled(binding.appbar.toolbar)

        if (savedInstanceState == null) {
            supportFragmentManager.commitNow {
                add(R.id.fragment_content, SettingsTelemetryFragment())
            }
        }
    }
}

@AndroidEntryPoint
class SettingsTelemetryFragment : Fragment(R.layout.fragment_telemetry_settings) {

    @Inject lateinit var mainScope: CoroutineScope
    @Inject lateinit var userSettingsManager: CurrentUserLocalSettingsManager
    @Inject lateinit var appConfig: AppConfig

    private val binding by viewBinding(FragmentTelemetrySettingsBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding.textTelemetryInfo) {
            text = HtmlTools.fromHtml(getString(R.string.settingsTelemetryScreenInfo, Constants.TELEMETRY_INFO_URL))
            movementMethod = LinkMovementMethod()
        }
        initEnableTelemetryToggle()
        initSendCrashReportsToggle()
    }

    private fun initEnableTelemetryToggle() = with(binding.switchEnableTelemetry) {
        isVisible = appConfig.getFeatureFlags().telemetry
        lifecycleScope.launch {
            isChecked = userSettingsManager.rawCurrentUserSettingsFlow.first().telemetry
            jumpDrawablesToCurrentState()
            setOnCheckedChangeListener { _, isChecked ->
                mainScope.launch { userSettingsManager.updateTelemetry(isChecked) }
            }
        }
    }

    private fun initSendCrashReportsToggle() = with(binding) {
        switchSendCrashReports.isChecked = SentryIntegration.isEnabled()
        switchSendCrashReports.setOnCheckedChangeListener { _, isChecked ->
            SentryIntegration.setEnabled(isChecked)
        }
    }
}
