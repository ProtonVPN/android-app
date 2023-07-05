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

package com.protonvpn.tests.redesign.recents

import androidx.room.Room
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.IdlingResource
import androidx.test.espresso.idling.CountingIdlingResource
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.db.AppDatabase
import com.protonvpn.android.db.AppDatabase.Companion.buildDatabase
import com.protonvpn.android.models.vpn.usecase.SupportsProtocol
import com.protonvpn.android.redesign.recents.data.RecentsDao
import com.protonvpn.android.redesign.recents.usecases.RecentsListValidator
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettingsCached
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestUser
import com.protonvpn.test.shared.createGetSmartProtocols
import com.protonvpn.test.shared.createInMemoryServersStore
import com.protonvpn.test.shared.createServer
import com.protonvpn.testsHelper.AccountTestHelper
import com.protonvpn.testsHelper.IdlingResourceDispatcher
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import me.proton.core.domain.entity.UserId
import me.proton.core.user.domain.entity.User
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executors
import kotlin.coroutines.resume

// These tests use mocking of final classes that's not available on API < 28
@SdkSuppress(minSdkVersion = 28)
@OptIn(ExperimentalCoroutinesApi::class)
class RecentsListValidatorTests {

    private lateinit var currentUserProvider: TestCurrentUserProvider
    private lateinit var currentUser: CurrentUser
    private lateinit var recentsDao: RecentsDao
    private lateinit var serverManager: ServerManager
    private lateinit var testScope: TestScope

    private lateinit var idlingResource: CountingIdlingResource

    private val userId1 = AccountTestHelper.TestAccount1.userId
    private val userId2 = AccountTestHelper.TestAccount2.userId

    @Before
    fun setup() {
        val testDispatcher = UnconfinedTestDispatcher()
        testScope = TestScope(testDispatcher)

        // Note: once we move to Room 2.5.1+ it should be possible to use a test Dispatcher with Room. Then there'll be
        // no need for idling resources and all this stuff.
        idlingResource = CountingIdlingResource("Dispatcher")
        val transactionExecutor = IdlingResourceDispatcher(
            Executors.newSingleThreadExecutor().asCoroutineDispatcher(),
            idlingResource
        ).asExecutor()
        IdlingRegistry.getInstance().register(idlingResource)

        currentUserProvider = TestCurrentUserProvider(TestUser.plusUser.vpnUser)
        currentUser = CurrentUser(testScope.backgroundScope, currentUserProvider)

        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(appContext, AppDatabase::class.java)
            .setQueryExecutor(Executors.newSingleThreadExecutor())
            .setTransactionExecutor(transactionExecutor)
            .buildDatabase()

        val accountHelper = AccountTestHelper()
        accountHelper.withAccountManager(db) { accountManager ->
            accountManager.addAccount(AccountTestHelper.TestAccount1, AccountTestHelper.TestSession1)
            accountManager.addAccount(AccountTestHelper.TestAccount2, AccountTestHelper.TestSession2)
        }

        recentsDao = db.recentsDao()
        serverManager = ServerManager(
            testScope.backgroundScope,
            EffectiveCurrentUserSettingsCached(MutableStateFlow(LocalUserSettings.Default)),
            currentUser = mockk(relaxed = true),
            wallClock = { 0 },
            supportsProtocol = SupportsProtocol(createGetSmartProtocols()),
            serversStore = createInMemoryServersStore(),
            profileManager = mockk(),
        )
    }

    @After
    fun tearDown() {
        IdlingRegistry.getInstance().unregister(idlingResource)
    }

    @Test
    fun whenServersAreRemovedThenRecentsPointingToTheseSpecificServersAreRemoved() = testScope.runTest {
        RecentsListValidator(backgroundScope, recentsDao, serverManager, currentUser)

        val servers = (1..4).map { number -> createServer("server$number") }
        serverManager.setServers(servers, null)

        val connectIntentsForServers = servers.map { server -> ConnectIntent.Server(server.serverId, emptySet()) }
        connectIntentsForServers.forEach {
            recentsDao.insertOrUpdateForConnection(userId1, it, 0L)
        }
        assertEquals(connectIntentsForServers.size, recentsDao.getRecentsList(userId1).first().size)

        val newServerList = listOf(servers[0], servers[3], createServer("server10"))
        serverManager.setServers(newServerList, null)

        // Let RecentsListValidator process the updates.
        idlingResource.waitUntilIdle()

        println("Finishing test")
        val remainingServers = newServerList.intersect(servers)
        val recents = recentsDao.getRecentsList(userId1).first()
        assertEquals(
            remainingServers.map { it.serverId },
            recents.map { (it.connectIntent as? ConnectIntent.Server)?.serverId ?: "invalid intent type" }
        )
    }

    @Test
    fun whenRecentsOverLimitAreRemovedOnlyCurrentUserIsAffected() = testScope.runTest {
        RecentsListValidator(backgroundScope, recentsDao, serverManager, currentUser)

        currentUserProvider.user = createUser(userId1)

        val servers = (1 .. 6).map { number -> createServer("server$number") }
        serverManager.setServers(servers, null)

        val intents = servers.map { server -> ConnectIntent.Server(server.serverId, emptySet()) }
        intents
            .forEachIndexed { index, intent -> recentsDao.insertOrUpdateForConnection(userId1, intent, index.toLong()) }
        intents
            .forEachIndexed { index, intent -> recentsDao.insertOrUpdateForConnection(userId2, intent, index.toLong()) }

        val intentOverLimit = ConnectIntent.Default
        recentsDao.insertOrUpdateForConnection(userId1, intentOverLimit, 100)

        // Let RecentsListValidator process the updates.
        idlingResource.waitUntilIdle()

        val user1Recents = recentsDao.getRecentsList(userId1).first()
        val user2Recents = recentsDao.getRecentsList(userId2).first()
        val reversedIntents = intents.reversed() // Recents are listed starting with most recent
        assertEquals(
            listOf(intentOverLimit) + reversedIntents.dropLast(1),
            user1Recents.map { it.connectIntent }
        )
        assertEquals(reversedIntents, user2Recents.map { it.connectIntent })
    }

    private suspend fun IdlingResource.waitUntilIdle() = suspendCancellableCoroutine { cont ->
        registerIdleTransitionCallback {
            cont.resume(Unit)
            registerIdleTransitionCallback(null)
        }
        cont.invokeOnCancellation { registerIdleTransitionCallback(null) }
    }

    private fun createUser(userId: UserId) =
        User(userId, "email", "name", null, "USD", 0, 0L, 0L, 0L, 0, null, false, 0, 0, null, null, emptyList())
}
