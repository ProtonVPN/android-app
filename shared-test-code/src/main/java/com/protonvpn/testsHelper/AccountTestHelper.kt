/*
 * Copyright (c) 2023. Proton AG
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

import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import me.proton.core.account.data.db.AccountDatabase
import me.proton.core.account.data.repository.AccountRepositoryImpl
import me.proton.core.account.domain.entity.Account
import me.proton.core.account.domain.entity.AccountDetails
import me.proton.core.account.domain.entity.AccountState
import me.proton.core.account.domain.entity.SessionState
import me.proton.core.accountmanager.data.AccountManagerImpl
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.crypto.android.context.AndroidCryptoContext
import me.proton.core.crypto.common.context.CryptoContext
import me.proton.core.crypto.common.keystore.EncryptedByteArray
import me.proton.core.crypto.common.keystore.EncryptedString
import me.proton.core.crypto.common.keystore.KeyStoreCrypto
import me.proton.core.crypto.common.keystore.PlainByteArray
import me.proton.core.domain.entity.Product
import me.proton.core.domain.entity.UserId
import me.proton.core.network.domain.session.Session
import me.proton.core.network.domain.session.SessionId
import me.proton.core.network.domain.session.SessionListener
import me.proton.core.test.kotlin.TestCoroutineScopeProvider

// TODO: we should upstream this to core modules.
class AccountTestHelper {

    fun withAccountManager(db: AccountDatabase, block: suspend (AccountManager) -> Unit) {
        val cryptoContext: CryptoContext = AndroidCryptoContext(
            keyStoreCrypto = object : KeyStoreCrypto {
                override fun isUsingKeyStore(): Boolean = false
                override fun encrypt(value: String): EncryptedString = value
                override fun decrypt(value: EncryptedString): String = value
                override fun encrypt(value: PlainByteArray): EncryptedByteArray =
                    EncryptedByteArray(value.array.copyOf())
                override fun decrypt(value: EncryptedByteArray): PlainByteArray = PlainByteArray(value.array.copyOf())
            }
        )

        val accountManager = AccountManagerImpl(
            Product.Vpn,
            TestCoroutineScopeProvider(),
            AccountRepositoryImpl(Product.Vpn, db, cryptoContext.keyStoreCrypto),
            mockk(relaxed = true),
            mockk(relaxed = true),
            TestSessionListener(),
        )
        runBlocking {
            block(accountManager)
        }
    }

    companion object {
        val UserId1 = UserId("user1")
        val UserId2 = UserId("user2")

        val TestSession1 = Session.Authenticated(
            userId = UserId1,
            sessionId = SessionId("session1"),
            accessToken = "accessToken1",
            refreshToken = "refreshToken1",
            scopes = emptyList()
        )
        val TestSession2 = Session.Authenticated(
            userId = UserId2,
            sessionId = SessionId("session1"),
            accessToken = "accessToken2",
            refreshToken = "refreshToken2",
            scopes = emptyList()
        )

        val TestAccount1 = Account(
            userId = UserId1,
            username = "userName",
            email = "email",
            state = AccountState.Ready,
            sessionId = TestSession1.sessionId,
            sessionState = SessionState.Authenticated,
            details = AccountDetails(null, null)
        )
        val TestAccount2 = Account(
            userId = UserId2,
            username = "userName",
            email = "email",
            state = AccountState.Ready,
            sessionId = TestSession2.sessionId,
            sessionState = SessionState.Authenticated,
            details = AccountDetails(null, null)
        )
    }
}

// TODO: this should be exported by Core.
private class TestSessionListener : SessionListener {
    override suspend fun requestSession(): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun refreshSession(session: Session): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun onSessionTokenCreated(userId: UserId?, session: Session) {
        TODO("Not yet implemented")
    }

    override suspend fun onSessionTokenRefreshed(session: Session) {
        TODO("Not yet implemented")
    }

    override suspend fun onSessionScopesRefreshed(sessionId: SessionId, scopes: List<String>) {
        TODO("Not yet implemented")
    }

    override suspend fun onSessionForceLogout(session: Session, httpCode: Int) {
        TODO("Not yet implemented")
    }

    override suspend fun <T> withLock(sessionId: SessionId?, action: suspend () -> T): T {
        return action()
    }
}
