/*
 * Copyright (c) 2021. Proton Technologies AG
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
import com.protonvpn.android.components.BaseActivityV2
import com.protonvpn.android.databinding.ActivityLoginHelpBinding
import com.protonvpn.android.utils.AndroidUtils.setContentViewBinding
import com.protonvpn.android.utils.openUrl
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LoginHelpActivity : BaseActivityV2() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = setContentViewBinding(ActivityLoginHelpBinding::inflate)
        initToolbarWithUpEnabled(binding.toolbar)
        title = null

        with(binding) {
            textForgotUsername.setOnClickListener { openUrl("https://account.protonvpn.com/forgot-username") }
            textForgotPassword.setOnClickListener { openUrl("https://account.protonvpn.com/reset-password") }
            textOtherIssues.setOnClickListener { openUrl("https://protonvpn.com/support/login-problems/") }
            textCustomerSupport.setOnClickListener { openUrl("https://protonvpn.com/support") }
        }
    }
}
