/*
 * Copyright (c) 2026. Proton AG
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

package com.protonvpn.robots.mobile

import com.protonvpn.android.R
import me.proton.test.fusion.Fusion.node
import me.proton.test.fusion.data.Robot

object SessionForkConfirmationRobot : Robot {

    private val createAccountButton get() = node.withText(R.string.session_fork_no_account_signup_button)
    private val signInAccountButton get() = node.withText(R.string.session_fork_no_account_signin_button)
    private val confirmForkButton get() = node.withText(R.string.login)
    private val forkSuccessTitle get() = node.withText(R.string.session_fork_success_title)
    private val genericErrorTitle get() = node.withText(R.string.session_fork_error_generic_title)
    private val tryAgainStoreButton get() = node.withText(R.string.try_again)

    fun confirmFork(): SessionForkConfirmationRobot {
        confirmForkButton.click()
        return this
    }

    fun tryAgain(): SessionForkConfirmationRobot {
        tryAgainStoreButton.click()
        return this
    }

    fun assertSignInDisplayed() {
        createAccountButton.assertIsDisplayed()
        signInAccountButton.assertIsDisplayed()
    }

    fun assertConfirmationDisplayed() {
        confirmForkButton.assertIsDisplayed()
    }

    fun assertErrorIsDisplayed() {
        genericErrorTitle.assertIsDisplayed()
    }

    fun assertForkSuccessIsDisplayed() {
        forkSuccessTitle.assertIsDisplayed()
    }

    fun assertTooSoonSnackIsDisplayed() {
        node.withText(R.string.session_fork_confirmation_too_soon_toast).assertIsDisplayed()
    }

    override fun robotDisplayed() {
        throw UnsupportedOperationException()
    }
}