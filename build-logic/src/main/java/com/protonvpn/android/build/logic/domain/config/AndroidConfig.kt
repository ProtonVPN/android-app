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

package com.protonvpn.android.build.logic.domain.config

import org.gradle.api.JavaVersion

internal object AndroidConfig {

    internal const val COMPILE_SDK_VERSION = 36

    internal const val MIN_SDK_VERSION = 26

    internal const val TARGET_SDK_VERSION = 35

    internal const val NDK_VERSION = "28.1.13356709"

    internal val CompileJavaVersion: JavaVersion = JavaVersion.VERSION_17

}
