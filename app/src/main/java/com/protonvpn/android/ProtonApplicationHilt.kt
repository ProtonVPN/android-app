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

import androidx.hilt.work.HiltWorkerFactory
import androidx.startup.AppInitializer
import androidx.work.Configuration
import com.protonvpn.android.ui.promooffers.TestNotificationLoader
import dagger.hilt.android.HiltAndroidApp
import me.proton.core.crypto.validator.presentation.init.CryptoValidatorInitializer
import javax.inject.Inject

@HiltAndroidApp
class ProtonApplicationHilt : ProtonApplication(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var testNotificationLoader: dagger.Lazy<TestNotificationLoader>

    override fun onCreate() {
        super.onCreate()
        initDependencies()

        // Manual triggering of androidx.startup initializers
        AppInitializer.getInstance(this).initializeComponent(CryptoValidatorInitializer::class.java)

        if (BuildConfig.DEBUG) {
            testNotificationLoader.get().loadTestFile()
        }
    }

    override fun getWorkManagerConfiguration(): Configuration =
        Configuration.Builder().setWorkerFactory(workerFactory).build()

}
