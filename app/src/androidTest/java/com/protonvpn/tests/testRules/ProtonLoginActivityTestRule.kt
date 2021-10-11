/*
 * Copyright (c) 2019 Proton Technologies AG
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
package com.protonvpn.tests.testRules

import androidx.test.rule.ActivityTestRule
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.ui.login.LoginActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.rules.TestWatcher
import org.junit.runner.Description

class ProtonLoginActivityTestRule : TestWatcher() {

    private val activityTestRule = ActivityTestRule(LoginActivity::class.java, false, false)
    private lateinit var userData: UserData

    override fun starting(description: Description) {
        activityTestRule.launchActivity(null)
        userData = activityTestRule.activity.viewModel.userData
    }

    override fun finished(description: Description) = runBlocking(Dispatchers.Main) {
        userData.logout()
    }

    fun getActivity(): LoginActivity? =
        activityTestRule.activity
}
