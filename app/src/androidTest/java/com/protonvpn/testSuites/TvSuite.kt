/*
 * Copyright (c) 2021 Proton AG
 * This file is part of Proton AG and ProtonCore.
 *
 * ProtonCore is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonCore is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonCore.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.protonvpn.testSuites

import com.protonvpn.tests.connection.tv.ConnectionTestsMocked
import com.protonvpn.tests.login.tv.LoginTestsMocked
import com.protonvpn.tests.login.tv.LogoutTestsMocked
import com.protonvpn.tests.reports.tv.TvBugReportTestsMocked
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    LoginTestsMocked::class,
    LogoutTestsMocked::class,
    ConnectionTestsMocked::class,
    TvBugReportTestsMocked::class,
)
class TvSuite
