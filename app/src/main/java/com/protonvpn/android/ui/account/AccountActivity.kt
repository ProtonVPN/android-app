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
package com.protonvpn.android.ui.account

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import com.protonvpn.android.BuildConfig
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseActivityV2
import com.protonvpn.android.databinding.ActivityAccountBinding
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.HtmlTools
import com.protonvpn.android.utils.ViewUtils.viewBinding
import com.protonvpn.android.utils.getThemeColor
import com.protonvpn.android.utils.openProtonUrl
import com.protonvpn.android.utils.toStringHtmlColorNoAlpha
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import me.proton.core.presentation.R as CoreR

@AndroidEntryPoint
class AccountActivity : BaseActivityV2() {

    private val viewModel: AccountActivityViewModel by viewModels()
    private val binding by viewBinding(ActivityAccountBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        initToolbarWithUpEnabled(binding.appbar.toolbar)

        lifecycleScope.launch {
            with(binding.content) {
                textUser.text = viewModel.displayName()
                textVersion.text = getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME
                val subscriptionDetailsHtml = getString(
                    R.string.accountSubscriptionDetails,
                    subscriptionDetails.getThemeColor(CoreR.attr.proton_text_accent).toStringHtmlColorNoAlpha()
                )
                subscriptionDetails.text = HtmlTools.fromHtml(subscriptionDetailsHtml)

                if (viewModel.purchaseEnabled()) {
                    buttonManageAccount.isVisible = true
                    buttonManageAccount.setOnClickListener {
                        openProtonUrl(Constants.ACCOUNT_LOGIN_URL)
                    }
                }
                buttonCoupon.setOnClickListener {
                    startActivity(Intent(this@AccountActivity, UseCouponActivity::class.java))
                }
            }
        }

        viewModel.viewState.asLiveData().observe(this, Observer { updateView(binding, it) })
    }

    private fun updateView(binding: ActivityAccountBinding, state: AccountActivityViewModel.ViewState) {
        with(binding.content) {
            textAccountTier.text = state.planName ?: getString(R.string.accountFree)
            buttonCoupon.isVisible = state.showCouponButton
        }
    }
}
