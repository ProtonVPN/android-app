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

package com.protonvpn.android.build.logic.plugins

import com.protonvpn.android.build.logic.domain.dependencies.PluginDependency
import com.protonvpn.android.build.logic.domain.plugins.AndroidLibraryConventionPlugin
import org.gradle.api.Project

internal class ProtonVpnAndroidLibraryConventionPlugin : AndroidLibraryConventionPlugin() {

    override fun apply(project: Project) = with(receiver = project) {
        applyPlugins(
            pluginDependencies = arrayOf(
                PluginDependency.AndroidLibrary,
                PluginDependency.KotlinAndroid,
            )
        )

        configureAndroidLibrary()
        configureKotlinOptions()
    }

}
