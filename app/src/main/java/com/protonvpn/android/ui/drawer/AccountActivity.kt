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
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.protonvpn.android.BuildConfig
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseActivityV2
import com.protonvpn.android.components.ContentLayout
import com.protonvpn.android.databinding.ActivityAccountBinding
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.HtmlTools
import com.protonvpn.android.utils.getThemeColor
import com.protonvpn.android.utils.openProtonUrl
import com.protonvpn.android.utils.toStringHtmlColorNoAlpha
import javax.inject.Inject

@ContentLayout(R.layout.activity_account)
class AccountActivity : BaseActivityV2<ActivityAccountBinding, AccountActivityViewModel>() {

    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory

    override fun initViewModel() {
        viewModel = ViewModelProvider(this, viewModelFactory).get(AccountActivityViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initToolbarWithUpEnabled(binding.appbar.toolbar)
        initContent()
    }

    private fun initContent() = with(binding.content) {
        textAccountTier.text = viewModel.accountTier(this@AccountActivity)
        viewModel.tierColor?.let {
            textAccountTier.setTextColor(ContextCompat.getColor(this@AccountActivity, it))
        }

        textUser.text = viewModel.user
        textAccountType.text = viewModel.accountType
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
