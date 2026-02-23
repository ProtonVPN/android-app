/*
 * Copyright (c) 2026 Proton AG
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

package com.protonvpn.app.mmp.referrer.usecases

import com.protonvpn.android.mmp.FakeIsMmpFeatureFlagEnabled
import com.protonvpn.android.mmp.referrer.data.MmpReferrerStorage
import com.protonvpn.android.mmp.referrer.usecases.FetchMmpReferrer
import com.protonvpn.android.mmp.referrer.usecases.GetMmpReferrer
import com.protonvpn.app.mmp.referrer.TestMmpReferrer
import com.protonvpn.test.shared.InMemoryDataStoreFactory
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class GetMmpReferrerTests {

    @MockK
    private lateinit var mockFetchMmpReferrer: FetchMmpReferrer

    private lateinit var isMmpEnabled: FakeIsMmpFeatureFlagEnabled

    private lateinit var mmpReferrerStorage: MmpReferrerStorage

    private lateinit var testScope: TestScope

    private lateinit var getMmpReferrer: GetMmpReferrer

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        isMmpEnabled = FakeIsMmpFeatureFlagEnabled(enabled = true)

        testScope = TestScope(context = UnconfinedTestDispatcher())

        mmpReferrerStorage = MmpReferrerStorage(
            mainScope = testScope.backgroundScope,
            localDataStoreFactory = InMemoryDataStoreFactory(),
        )

        getMmpReferrer = GetMmpReferrer(
            isMmpEnabled = isMmpEnabled,
            mmpReferrerStorage = mmpReferrerStorage,
            fetchMmpReferrer = mockFetchMmpReferrer,
        )
    }

    @Test
    fun `GIVEN feature flag enabled AND no referrer stored locally WHEN getting referrer THEN return referrer from remote data source`() = testScope.runTest {
        isMmpEnabled.setEnabled(isEnabled = true)
        val expectedMmpReferrer = TestMmpReferrer.create()
        coEvery { mockFetchMmpReferrer.getMmpReferrer() } returns expectedMmpReferrer

        val referrer = getMmpReferrer()

        assertEquals(expected = expectedMmpReferrer, actual = referrer)
    }

    @Test
    fun `GIVEN feature flag disabled AND no referrer stored locally WHEN getting referrer THEN returns null`() = testScope.runTest {
        isMmpEnabled.setEnabled(isEnabled = false)

        val referrer = getMmpReferrer()

        assertNull(actual = referrer)
    }

    @Test
    fun `GIVEN feature flag enabled AND referrer is stored locally WHEN getting referrer THEN return referrer from local data source`() = testScope.runTest {
        isMmpEnabled.setEnabled(isEnabled = true)
        val expectedMmpReferrer = TestMmpReferrer.create()
        mmpReferrerStorage.setMmpReferrer(mmpReferrer = expectedMmpReferrer)

        val referrer = getMmpReferrer()

        assertEquals(expected = expectedMmpReferrer, actual = referrer)
    }

    @Test
    fun `GIVEN feature flag disabled AND referrer is stored locally WHEN getting referrer THEN returns null`() = testScope.runTest {
        isMmpEnabled.setEnabled(isEnabled = false)
        mmpReferrerStorage.setMmpReferrer(mmpReferrer = TestMmpReferrer.create())

        val referrer = getMmpReferrer()

        assertNull(actual = referrer)
    }

}
