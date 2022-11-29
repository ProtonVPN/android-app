/*
 * Copyright (c) 2021 Proton Technologies AG
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

package com.protonvpn.android.auth.usecase

import com.protonvpn.android.api.HumanVerificationHandler
import com.protonvpn.android.auth.data.VpnUserDao
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.LogLevel
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.login.LoginResponse
import com.protonvpn.android.models.login.toVpnUserEntity
import com.protonvpn.android.utils.Storage
import dagger.Reusable
import io.sentry.Sentry
import kotlinx.coroutines.runBlocking
import me.proton.core.account.domain.entity.Account
import me.proton.core.account.domain.entity.AccountDetails
import me.proton.core.account.domain.entity.AccountState
import me.proton.core.account.domain.entity.SessionState
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.domain.entity.UserId
import me.proton.core.humanverification.domain.repository.HumanVerificationRepository
import me.proton.core.network.domain.session.Session
import me.proton.core.util.kotlin.takeIfNotBlank
import javax.inject.Inject

@Reusable
class CoreLoginMigration @Inject constructor(
    private val accountManager: AccountManager,
    private val vpnUserDao: VpnUserDao,
    private val humanVerificationRepository: HumanVerificationRepository,
    private val userData: UserData,
) {
    fun migrateIfNeeded() {
        if (userData.migrateIsLoggedIn) {
            val session = Storage.load(LoginResponse::class.java)
            val user = userData.migrateUser?.takeIfNotBlank() ?: "unknown"
            val vpnInfo = userData.migrateVpnInfoResponse
            if (session != null && vpnInfo != null && session.userId != null) {
                val userId = UserId(session.userId)
                val vpnUserEntity = runCatching { vpnInfo.toVpnUserEntity(userId, session.sessionId) }
                if (vpnUserEntity.isSuccess) {
                    // Run migration as blocking to avoid race conditions with synchronous user code
                    runBlocking {
                        accountManager.addAccount(
                            Account(
                                userId,
                                user,
                                if ('@' in user) user else "$user@protonmail.com",
                                AccountState.Ready,
                                session.sessionId,
                                SessionState.Authenticated,
                                AccountDetails(null, null)
                            ), Session(
                                session.sessionId,
                                session.accessToken,
                                session.refreshToken,
                                session.scope.split(" ")
                            )
                        )

                        vpnUserDao.insertOrUpdate(vpnUserEntity.getOrThrow())

                        Storage.load(HumanVerificationHandler.HumanVerificationDetailsData::class.java)?.let { data ->
                            for ((_, details) in data.details)
                                humanVerificationRepository.insertHumanVerificationDetails(details)

                            Storage.delete(HumanVerificationHandler.HumanVerificationDetailsData::class.java)
                        }
                    }
                } else {
                    val exception = vpnUserEntity.exceptionOrNull()!!
                    ProtonLogger.logCustom(
                        LogLevel.ERROR,
                        LogCategory.APP,
                        "Unable to migrate user data: $exception"
                    )
                    Sentry.captureException(exception)
                }
            }
            Storage.delete(LoginResponse::class.java)
            userData.finishUserMigration()
        }
    }
}
