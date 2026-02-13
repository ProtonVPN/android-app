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

package com.protonvpn.android.build.logic.domain.plugins

import com.android.build.api.dsl.LibraryExtension
import com.protonvpn.android.build.logic.domain.config.AndroidConfig
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

internal abstract class AndroidLibraryConventionPlugin : ConventionPlugin() {

    protected fun Project.configureAndroidLibrary() {
        extensions.configure<LibraryExtension> {
            compileSdk = AndroidConfig.COMPILE_SDK_VERSION
            ndkVersion = AndroidConfig.NDK_VERSION

            defaultConfig {
                minSdk = AndroidConfig.MIN_SDK_VERSION
            }

            compileOptions {
                sourceCompatibility = AndroidConfig.CompileJavaVersion
                targetCompatibility = AndroidConfig.CompileJavaVersion
            }
        }
    }

}
