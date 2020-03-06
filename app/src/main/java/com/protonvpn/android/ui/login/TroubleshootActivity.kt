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
package com.protonvpn.android.ui.login

import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseActivityV2
import com.protonvpn.android.components.ContentLayout
import com.protonvpn.android.databinding.ActivityTroubleshootBinding
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.HtmlTools
import javax.inject.Inject

@ContentLayout(R.layout.activity_troubleshoot)
class TroubleshootActivity : BaseActivityV2<ActivityTroubleshootBinding, TroubleshootViewModel>() {

    companion object {
        private const val TOR_URL = "https://www.torproject.org/"
        private const val PROTON_STATUS_URL = "https://protonstatus.com/"
        private const val SUPPORT_URL = "https://protonvpn.com/support-form"
        private const val MAIL_URL = "mailto:support@protonvpn.com"
        private const val TWITTER_URL = "https://twitter.com/ProtonVPN"
    }

    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initToolbarWithUpEnabled(binding.appbar.toolbar)

        with(binding.content) {
            switchDnsOverHttps.setDescription(HtmlTools.fromHtml(getString(
                    R.string.settingsAllowAlternativeRoutingDescription, Constants.ALTERNATIVE_ROUTING_LEARN_URL)))
            switchDnsOverHttps.switchProton.isChecked = viewModel.userData.apiUseDoH
            switchDnsOverHttps.switchProton.setOnCheckedChangeListener { _, checked ->
                viewModel.dnsOverHttpsEnabled = checked
            }

            switchIspProblem.setDescription(HtmlTools.fromHtml(getString(
                    R.string.troubleshootIspProblemDescription, TOR_URL)))

            switchGovBlock.setDescription(HtmlTools.fromHtml(getString(
                    R.string.troubleshootGovernmentBlockDescription, TOR_URL)))

            switchProtonDown.setDescription(HtmlTools.fromHtml(getString(
                    R.string.troubleshootProtonDownDescription, PROTON_STATUS_URL)))

            switchOtherProblem.setDescription(HtmlTools.fromHtml(getString(
                    R.string.troubleshootOtherProblemDescription, SUPPORT_URL, MAIL_URL, TWITTER_URL)))
        }
    }

    override fun initViewModel() {
        viewModel = ViewModelProvider(this, viewModelFactory).get(TroubleshootViewModel::class.java)
    }
}
