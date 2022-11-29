/*
 * Copyright (c) 2022. Proton AG
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

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.protonvpn.android.ui.drawer.bugreport.DynamicReportActivity
import dagger.Reusable
import me.proton.core.auth.presentation.DefaultHelpOptionHandler
import javax.inject.Inject

@Reusable
class VpnHelpOptionHandler @Inject constructor() : DefaultHelpOptionHandler() {

    override fun onCustomerSupport(context: AppCompatActivity) {
        context.startActivity(Intent(context, DynamicReportActivity::class.java))
    }
}
