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
package com.protonvpn.testRules

import com.protonvpn.android.utils.Storage
import com.protonvpn.test.shared.TestUser
import com.protonvpn.testsHelper.TestSetup.Companion.setCompletedOnboarding
import com.protonvpn.testsHelper.UserDataHelper
import org.junit.rules.TestWatcher
import org.junit.runner.Description

class SetUserPreferencesRule(var user: TestUser) : TestWatcher() {
    override fun starting(description: Description) {
        setCompletedOnboarding()
        UserDataHelper().setUserData(user)
    }

    override fun finished(description: Description) {
        Storage.clearAllPreferencesSync()
    }
}
