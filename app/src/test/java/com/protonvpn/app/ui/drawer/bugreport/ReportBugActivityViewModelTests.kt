/*
 * Copyright (c) 2024. Proton AG
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

package com.protonvpn.app.ui.drawer.bugreport

import com.protonvpn.android.api.DohEnabled
import com.protonvpn.android.auth.AuthFlowStartHelper
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.concurrency.VpnDispatcherProvider
import com.protonvpn.android.tv.IsTvCheck
import com.protonvpn.android.ui.drawer.bugreport.PrepareAndPostBugReport
import com.protonvpn.android.ui.drawer.bugreport.ReportBugActivityViewModel
import com.protonvpn.app.testRules.RobolectricHiltAndroidRule
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestUser
import com.protonvpn.test.shared.createAccountUser
import com.protonvpn.test.shared.runWhileCollecting
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import me.proton.core.user.domain.entity.Type
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import javax.inject.Inject
import kotlin.test.assertIs

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
class ReportBugActivityViewModelTests {

    @get:Rule
    val roboRule = RobolectricHiltAndroidRule(this)

    @Inject
    lateinit var dohEnabledProvider: DohEnabled.Provider // Provider is needed to unblock DohEnabled.
    @Inject
    lateinit var testScope: TestScope
    @Inject
    lateinit var currentUser: CurrentUser
    @Inject
    lateinit var dispatcherProvider: VpnDispatcherProvider
    @Inject
    lateinit var isTv: IsTvCheck
    @Inject
    lateinit var authFlowStartHelper: AuthFlowStartHelper
    @Inject
    lateinit var prepareAndPostBugReport: PrepareAndPostBugReport
    @Inject
    lateinit var testCurrentUserProvider: TestCurrentUserProvider

    lateinit var viewModel: ReportBugActivityViewModel

    private val vpnUser = TestUser.freeUser.vpnUser
    private val accountUser = createAccountUser(vpnUser.userId)
    private val accountCredlessUser = createAccountUser(vpnUser.userId, type = Type.CredentialLess)

    @Before
    fun setup() {
        roboRule.inject()
        viewModel = ReportBugActivityViewModel(
            testScope,
            dispatcherProvider,
            currentUser,
            isTv,
            authFlowStartHelper,
            prepareAndPostBugReport
        )
    }

    @Test
    fun `when no user is logged in then navigate through the form`() = testScope.runTest {
        val step1 = viewModel.state.value
        assertIs<ReportBugActivityViewModel.ViewState.Categories>(step1)
        assertTrue(step1.categoryList.isNotEmpty())

        val category1 = step1.categoryList.first()
        viewModel.navigateToSuggestions(category1)
        val step2 = viewModel.state.value
        assertIs<ReportBugActivityViewModel.ViewState.Suggestions>(step2)

        viewModel.navigateToReport(category1)
        val step3 = viewModel.state.value
        assertIs<ReportBugActivityViewModel.ViewState.Report>(step3)
    }

    @Test
    fun `when regular user is logged in then navigate to report`() = testScope.runTest {
        testCurrentUserProvider.set(vpnUser, accountUser)
        val step1 = viewModel.state.value
        assertIs<ReportBugActivityViewModel.ViewState.Categories>(step1)
        assertTrue(step1.categoryList.isNotEmpty())

        viewModel.navigateToReport(step1.categoryList.first())
        val step2 = viewModel.state.value
        assertIs<ReportBugActivityViewModel.ViewState.Report>(step2)
    }

    @Test
    fun `when credentialless user is logged in then they must create an account`() = testScope.runTest {
        testCurrentUserProvider.set(vpnUser, accountCredlessUser)
        val step1 = viewModel.state.value
        assertIs<ReportBugActivityViewModel.ViewState.Categories>(step1)
        assertTrue(step1.categoryList.isNotEmpty())

        val events = runWhileCollecting(viewModel.event) {
            viewModel.navigateToReport(step1.categoryList.first())
            val step2 = viewModel.state.value
            assertIs<ReportBugActivityViewModel.ViewState.Categories>(step2)
        }
        assertEquals(listOf(ReportBugActivityViewModel.UiEvent.ShowLoginDialog), events)
    }
}
