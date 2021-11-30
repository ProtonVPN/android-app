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

import com.protonvpn.android.auth.usecase.CoreLoginMigration
import com.protonvpn.android.vpn.LogcatLogCapture
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import me.proton.core.accountmanager.data.AccountStateHandler
import javax.inject.Inject

@HiltAndroidApp
class ProtonApplicationHilt : ProtonApplication() {

    @Inject lateinit var logcatLogCapture: LogcatLogCapture

    // Lazy as some of the dependencies assume application is created (and available through static getter).
    @Inject lateinit var coreLoginMigration: Lazy<CoreLoginMigration>
    @Inject lateinit var accountStateHandler: Lazy<AccountStateHandler>

    override fun onCreate() {
        super.onCreate()
        logcatLogCapture.startCapture()
        accountStateHandler.get().start()
        coreLoginMigration.get().migrateIfNeeded()
    }
}
