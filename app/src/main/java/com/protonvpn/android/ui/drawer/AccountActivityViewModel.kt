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
package com.protonvpn.android.ui.drawer

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import com.protonvpn.android.R
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.utils.HtmlTools
import javax.inject.Inject

class AccountActivityViewModel @Inject constructor(val userData: UserData) : ViewModel() {

    val user get(): CharSequence? = if (userData.vpnInfoResponse != null) userData.user else "development@protonvpn.com"
    val accountType get(): CharSequence? = HtmlTools.fromHtml(userData.vpnInfoResponse?.accountType) ?: "Debug"

    fun accountTier(context: Context) = userTierName?.let { context.getText(getAccountTypeNaming(it)) } ?: "development"

    val tierColor get() = when (userTierName) {
        "vpnbasic" -> R.color.accountTypeBasic
        "vpnplus" -> R.color.accountTypePlus
        "visionary" -> R.color.accountTypeVisionary
        else -> null
    }

    @StringRes
    private fun getAccountTypeNaming(accountType: String) = when (accountType) {
        "trial" -> R.string.accountTrial
        "free" -> R.string.accountFree
        "vpnbasic" -> R.string.accountBasic
        "vpnplus" -> R.string.accountPlus
        "visionary" -> R.string.accountVisionary
        else -> R.string.accountFree
    }

    private val userTierName get() = userData.vpnInfoResponse?.userTierName
}
