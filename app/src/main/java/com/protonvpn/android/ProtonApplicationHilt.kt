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

package com.protonvpn.android

import android.webkit.WebView
import androidx.hilt.work.HiltWorkerFactory
import androidx.startup.AppInitializer
import androidx.work.Configuration
import com.protonvpn.android.logging.MemoryMonitor
import com.protonvpn.android.ui.promooffers.TestNotificationLoader
import com.protonvpn.android.utils.isMainProcess
import dagger.hilt.android.HiltAndroidApp
import me.proton.core.auth.presentation.MissingScopeInitializer
import me.proton.core.crypto.validator.presentation.init.CryptoValidatorInitializer
import me.proton.core.network.presentation.init.UnAuthSessionFetcherInitializer
import me.proton.core.plan.presentation.UnredeemedPurchaseInitializer
import javax.inject.Inject

@HiltAndroidApp
class ProtonApplicationHilt : ProtonApplication(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var testNotificationLoader: dagger.Lazy<TestNotificationLoader>
    @Inject lateinit var updateMigration: UpdateMigration
    @Inject lateinit var memoryMonitor: dagger.Lazy<MemoryMonitor>

    override fun onCreate() {
        super.onCreate()
        if (isMainProcess()) {
            initDependencies()

            // Manual triggering of androidx.startup initializers (only for functionality that MUST NOT run in TESTS)
            // Initialize most objects in ProtonApplication.initDependencies().
            AppInitializer.getInstance(this).initializeComponent(CryptoValidatorInitializer::class.java)
            AppInitializer.getInstance(this).initializeComponent(MissingScopeInitializer::class.java)
            AppInitializer.getInstance(this).initializeComponent(UnredeemedPurchaseInitializer::class.java)
            AppInitializer.getInstance(this).initializeComponent(UnAuthSessionFetcherInitializer::class.java)
            if (BuildConfig.DEBUG) {
                WebView.setWebContentsDebuggingEnabled(true)
                testNotificationLoader.get().loadTestFile()
            }

            updateMigration.handleUpdate()
            memoryMonitor.get().start()
        }
    }

    override fun getWorkManagerConfiguration(): Configuration =
        Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        memoryMonitor.get().onTrimMemory()
    }
}
