/*
 * Copyright (c) 2022. Proton AG
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

import android.util.Log
import androidx.test.espresso.idling.CountingIdlingResource
import com.protonvpn.android.concurrency.VpnDispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext

/**
 * CouroutineDispatchers that can be registered with IdlingResource to notify Espresso the app is busy while
 * there is work executed in the background.
 *
 * @see ProtonHiltAndroidRule
 */
@Singleton
class EspressoDispatcherProvider @Inject constructor() : VpnDispatcherProvider {
    override val Main: CoroutineDispatcher = IdlingResourceDispatcher(Dispatchers.Main)
    override val Comp: CoroutineDispatcher = IdlingResourceDispatcher(Dispatchers.Default)
    override val Io: CoroutineDispatcher = IdlingResourceDispatcher(Dispatchers.IO)
    override val infiniteIo = Dispatchers.IO // Ignore status of infinite tasks, otherwise tests will wait forever.

    override fun newSingleThreadDispatcher(): CoroutineDispatcher =
        IdlingResourceDispatcher(Executors.newSingleThreadExecutor().asCoroutineDispatcher())

    override fun newSingleThreadDispatcherForInifiniteIo(): CoroutineDispatcher =
        Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    val idlingResource = CountingIdlingResource("Dispatcher provider")

    private inner class IdlingResourceDispatcher(private val dispatcher: CoroutineDispatcher) : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            idlingResource.increment()
            idlingResource.dumpStateToLogs()
            Log.d("Idling", "Block $block")
            dispatcher.dispatch(
                context,
                Runnable {
                    try {
                        block.run()
                    } finally {
                        idlingResource.decrement()
                        idlingResource.dumpStateToLogs()
                    }
                }
            )
        }
    }
}
