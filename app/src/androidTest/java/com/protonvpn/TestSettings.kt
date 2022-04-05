/*
 *  Copyright (c) 2021 Proton AG
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

package com.protonvpn

/**
 * Settings for UI tests.
 *
 * Some settings may impact what dependencies are built by Hilt. They need to be set before Hilt components are created.
 * Use TestSettingsOverrideRule around ProtonHiltAndroidRule to achieve this.
 */
object TestSettings {
    @JvmStatic var mockedConnectionUsed = true
    @JvmStatic var testRailReportingUsed = true
}
