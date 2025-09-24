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

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.test.espresso.idling.CountingIdlingResource
import com.protonvpn.android.concurrency.VpnDispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.android.asCoroutineDispatcher
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
    val idlingResource = CountingIdlingResource("Dispatcher provider")

    override val Main: CoroutineDispatcher = IdlingResourceDispatcher(
        // Can't use Dispatchers.Main here because it will be overridden by
        // Dispatchers.setMain(EspressoDispatcherProvider.Main) in tests causing an infinite recursion when the
        // dispatch() method is called.
        Handler(Looper.getMainLooper()).asCoroutineDispatcher("EspressoDispatcherProvider.Main"),
        idlingResource
    )
    override val Comp: CoroutineDispatcher = IdlingResourceDispatcher(Dispatchers.Default, idlingResource)
    override val Io: CoroutineDispatcher = IdlingResourceDispatcher(Dispatchers.IO, idlingResource)
    override val infiniteIo = Dispatchers.IO // Ignore status of infinite tasks, otherwise tests will wait forever.

    override fun newSingleThreadDispatcher(): CoroutineDispatcher =
        IdlingResourceDispatcher(Executors.newSingleThreadExecutor().asCoroutineDispatcher(), idlingResource)

    override fun newSingleThreadDispatcherForInifiniteIo(): CoroutineDispatcher =
        Executors.newSingleThreadExecutor().asCoroutineDispatcher()
}

class IdlingResourceDispatcher(
    private val dispatcher: CoroutineDispatcher,
    private val idlingResource: CountingIdlingResource
) : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        idlingResource.increment()
        dispatcher.dispatch(
            context,
            Runnable {
                try {
                    block.run()
                } finally {
                    idlingResource.decrement()
                }
            }
        )
    }
}
