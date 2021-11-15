/*
 * Copyright (c) 2017 Proton Technologies AG
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

import android.os.Bundle
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.protonvpn.android.BuildConfig
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseActivityV2
import com.protonvpn.android.databinding.ActivityAccountBinding
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.HtmlTools
import com.protonvpn.android.utils.getThemeColor
import com.protonvpn.android.utils.openProtonUrl
import com.protonvpn.android.utils.toStringHtmlColorNoAlpha
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AccountActivity : BaseActivityV2() {

    private val viewModel: AccountActivityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityAccountBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initToolbarWithUpEnabled(binding.appbar.toolbar)

        lifecycleScope.launch {
            with(binding.content) {
                textAccountTier.text = viewModel.accountTier(this@AccountActivity)

                textUser.text = viewModel.displayName()
                textVersion.text = getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME
                val subscriptionDetailsHtml = getString(
                    R.string.accountSubscriptionDetails,
                    subscriptionDetails.getThemeColor(R.attr.colorAccent).toStringHtmlColorNoAlpha()
                )
                subscriptionDetails.text = HtmlTools.fromHtml(subscriptionDetailsHtml)

                buttonManageAccount.setOnClickListener {
                    openProtonUrl(Constants.ACCOUNT_LOGIN_URL)
                }
            }
        }
    }
}
