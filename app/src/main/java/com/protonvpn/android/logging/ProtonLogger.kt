/*
 * Copyright (c) 2019 Proton Technologies AG
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
package com.protonvpn.android.logging

import com.protonvpn.android.ProtonApplication
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

object ProtonLogger : ProtonLoggerImpl(
    System::currentTimeMillis,
    FileLogWriter(
        ProtonApplication.getAppContext(),
        MainScope(),
        Executors.newSingleThreadExecutor().asCoroutineDispatcher(),
        ProtonApplication.getAppContext().applicationInfo.dataDir + "/log",
        CurrentStateLoggerGlobal(ProtonApplication.getAppContext()),
    ),
    SentryLogWriter(ProtonApplication.getAppContext())
)
