/*
 *
 *  * Copyright (c) 2023. Proton AG
 *  *
 *  * This file is part of ProtonVPN.
 *  *
 *  * ProtonVPN is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * ProtonVPN is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package com.protonvpn.tests.recents

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
import com.protonvpn.android.profiles.data.ProfilesDao
import com.protonvpn.android.redesign.recents.data.RecentsDao
import com.protonvpn.android.redesign.recents.data.toConnectIntent
import com.protonvpn.android.redesign.recents.usecases.RecentsListValidator
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.servers.ServerManager2
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.mocks.createInMemoryServerManager
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestDispatcherProvider
import com.protonvpn.test.shared.TestUser
import com.protonvpn.test.shared.createAccountUser
import com.protonvpn.test.shared.createGetSmartProtocols
import com.protonvpn.test.shared.createProfileEntity
import com.protonvpn.test.shared.createServer
import com.protonvpn.testsHelper.AccountTestHelper
import com.protonvpn.testsHelper.IdlingResourceDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.test.assertNotNull

// These tests use mocking of final classes that's not available on API < 28
@SdkSuppress(minSdkVersion = 28)
@OptIn(ExperimentalCoroutinesApi::class)
class RecentsListValidatorTestsIntegration {

    private lateinit var currentUserProvider: TestCurrentUserProvider
    private lateinit var currentUser: CurrentUser
    private lateinit var recentsDao: RecentsDao
    private lateinit var profilesDao: ProfilesDao
    private lateinit var serverManager: ServerManager
    private lateinit var serverManager2: ServerManager2
    private lateinit var testScope: TestScope
    private lateinit var settingsFlow: StateFlow<LocalUserSettings>
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
        currentUser = CurrentUser(currentUserProvider)
        settingsFlow = MutableStateFlow(LocalUserSettings.Default)
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(appContext, AppDatabase::class.java)
            .setQueryExecutor(transactionExecutor)
            .setTransactionExecutor(transactionExecutor)
            .buildDatabase()

        val accountHelper = AccountTestHelper()
        accountHelper.withAccountManager(db) { accountManager ->
            accountManager.addAccount(AccountTestHelper.TestAccount1, AccountTestHelper.TestSession1)
            accountManager.addAccount(AccountTestHelper.TestAccount2, AccountTestHelper.TestSession2)
        }

        val supportsProtocol = SupportsProtocol(createGetSmartProtocols())
        recentsDao = db.recentsDao()
        profilesDao = db.profilesDao()
        serverManager = createInMemoryServerManager(
            testScope,
            TestDispatcherProvider(testDispatcher),
            supportsProtocol,
            emptyList()
        )
        serverManager2 = ServerManager2(serverManager, supportsProtocol)
    }

    @After
    fun tearDown() {
        IdlingRegistry.getInstance().unregister(idlingResource)
    }

    @Test
    fun whenServersAreRemovedThenUnnamedRecentsPointingToTheseSpecificServersAreRemoved() = testScope.runTest {
        RecentsListValidator(backgroundScope, recentsDao, serverManager2, currentUser)

        val servers = (1..4).map { number -> createServer("server$number") }
        serverManager.setServers(servers, "StatusID", null)

        val connectIntentsForServers = servers.map { server -> ConnectIntent.fromServer(server, emptySet()) }
        connectIntentsForServers.forEach {
            recentsDao.insertOrUpdateForConnection(userId1, it, 0L)
        }
        val profileEntity = createProfileEntity(userId = userId1, connectIntent = ConnectIntent.fromServer(servers[1], emptySet()))
        profilesDao.upsert(profileEntity)
        recentsDao.insertOrUpdateForConnection(userId1, profileEntity.connectIntentData.toConnectIntent(), 0L)
        assertEquals(connectIntentsForServers.size + 1, recentsDao.getRecentsList(userId1).first().size)

        val newServerList = listOf(servers[0], servers[3], createServer("server10"))
        serverManager.setServers(newServerList, "StatusID", null)

        // Let RecentsListValidator process the updates.
        idlingResource.waitUntilIdle()

        println("Finishing test")
        // server 1 is present as it's referenced by a profile
        val remainingServers = newServerList.intersect(servers.toSet()) + servers[1]
        val recents = recentsDao.getRecentsList(userId1).first()
        assertEquals(
            remainingServers.map { it.serverId }.toSet(),
            recents.map { (it.connectIntent as? ConnectIntent.Server)?.serverId ?: "invalid intent type" }.toSet()
        )
        // Profile was not removed from recents
        assertNotNull(recents.find { it.connectIntent.profileId == profileEntity.connectIntentData.profileId })
    }

    @Test
    fun whenRecentsOverLimitAreRemovedOnlyCurrentUserIsAffected() = testScope.runTest {
        RecentsListValidator(backgroundScope, recentsDao, serverManager2, currentUser)

        currentUserProvider.user = createAccountUser(userId1)

        val servers = (1 .. 6).map { number -> createServer("server$number") }
        serverManager.setServers(servers, "StatusID", null)

        val intents = servers.map { server -> ConnectIntent.fromServer(server, emptySet()) }
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

    private suspend fun IdlingResource.waitUntilIdle() {
        yield()
        suspendCancellableCoroutine { cont ->
            registerIdleTransitionCallback {
                cont.resume(Unit)
                registerIdleTransitionCallback(null)
            }
            cont.invokeOnCancellation { registerIdleTransitionCallback(null) }
        }
    }
}
