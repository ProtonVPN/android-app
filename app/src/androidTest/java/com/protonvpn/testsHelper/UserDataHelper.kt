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
package com.protonvpn.testsHelper

import androidx.test.platform.app.InstrumentationRegistry
import com.protonvpn.android.ProtonApplication
import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.data.VpnUserDao
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.auth.usecase.Logout
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.login.VPNInfo
import com.protonvpn.android.models.login.VpnInfoResponse
import com.protonvpn.android.models.login.toVpnUserEntity
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.tv.IsTvCheck
import com.protonvpn.android.tv.TvLoginActivity
import com.protonvpn.android.vpn.ProtocolSelection
import com.protonvpn.mocks.MockUserRepository
import com.protonvpn.test.shared.TestUser
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.runBlocking
import me.proton.core.account.domain.entity.Account
import me.proton.core.account.domain.entity.AccountDetails
import me.proton.core.account.domain.entity.AccountState
import me.proton.core.account.domain.entity.SessionState
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.auth.presentation.ui.LoginActivity
import me.proton.core.domain.entity.UserId
import me.proton.core.network.domain.session.Session
import me.proton.core.user.domain.entity.Role
import me.proton.core.user.domain.entity.Type
import me.proton.core.user.domain.entity.User

class UserDataHelper {

    @JvmField var logoutUseCase: Logout
    @JvmField var accountManager: AccountManager
    @JvmField var currentUser: CurrentUser
    private var isTv: IsTvCheck
    @JvmField var vpnUserDao: VpnUserDao
    @JvmField var userRepository: MockUserRepository
    var userSettingsManager: CurrentUserLocalSettingsManager

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface UserDataHelperEntryPoint {
        fun accountManager(): AccountManager
        fun currentUser(): CurrentUser
        fun isTv(): IsTvCheck
        fun logoutUseCase(): Logout
        fun mockUserRepository(): MockUserRepository
        fun userSettingsManager(): CurrentUserLocalSettingsManager
        fun vpnUserDao(): VpnUserDao
    }

    init {
        runBlocking(Main.immediate) {
            val hiltEntry = EntryPoints.get(
                ProtonApplication.getAppContext(), UserDataHelperEntryPoint::class.java)
            accountManager = hiltEntry.accountManager()
            currentUser = hiltEntry.currentUser()
            isTv = hiltEntry.isTv()
            logoutUseCase = hiltEntry.logoutUseCase()
            userRepository = hiltEntry.mockUserRepository()
            userSettingsManager = hiltEntry.userSettingsManager()
            vpnUserDao = hiltEntry.vpnUserDao()
        }
    }

    private fun getUser(userId: UserId, user: TestUser) = User(
        userId = userId,
        email = user.email,
        name = user.email,
        displayName = user.email,
        currency = "CHF",
        type = Type.Proton,
        credit = 0,
        createdAtUtc = 0L,
        usedSpace = 0L,
        maxSpace = 1L,
        maxUpload = 1L,
        role = Role.NoOrganization,
        private = false,
        services = 0,
        subscribed = 0,
        delinquent = null,
        recovery = null,
        keys = emptyList(),
        maxBaseSpace = null,
        maxDriveSpace = null,
        usedBaseSpace = null,
        usedDriveSpace = null,
        flags = emptyMap()
    )

    fun setUserData(user: TestUser) = runBlocking(Main) {
        val sessionId = user.vpnUser.sessionId
        val userId = user.vpnUser.userId
        accountManager.addAccount(
            Account(userId, user.email, user.email, AccountState.Ready, sessionId, SessionState.Authenticated,
                AccountDetails(null, null)),
            Session.Authenticated(userId, sessionId, "accessToken", "refreshToken", emptyList()))

        vpnUserDao.insertOrUpdate(user.vpnInfoResponse.toVpnUserEntity(userId, sessionId, 0, null))
        userRepository.setMockUser(getUser(userId, user))
    }

    fun setProtocol(protocol: VpnProtocol, transmission: TransmissionProtocol? = null) = runBlocking(Main) {
        userSettingsManager.updateProtocol(ProtocolSelection(protocol, transmission))
    }

    fun logoutUser() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // Logging out starts the login activity, block it, otherwise it may crash when starting
        // after the test has finished and Hilt can no longer provide dependencies.
        val loginActivityClass =
            if (isTv()) TvLoginActivity::class.java
            else LoginActivity::class.java
        val monitor =
            instrumentation.addMonitor(loginActivityClass.canonicalName, null, true)
        runBlocking(Main) {
            logoutUseCase()
        }
        // Remove the monitor so that it doesn't avoid any other tests.
        if (!instrumentation.checkMonitorHit(monitor, 1)) {
            monitor.waitForActivityWithTimeout(1000)
            instrumentation.removeMonitor(monitor)
        }
    }
}

fun VpnUser.toVpnInfoResponse() = VpnInfoResponse(1000, VPNInfo(status, expirationTime, planName, planDisplayName, maxTier,
    maxConnect, name, groupId, password), subscribed, services, delinquent, credit, hasPaymentMethod)
