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

import com.protonvpn.android.build.logic.domain.dependencies.PluginDependency
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.PluginAware
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

internal abstract class ConventionPlugin : Plugin<Project> {

    protected fun Project.applyPlugins(vararg pluginDependencies: PluginDependency) {
        pluginDependencies.forEach { pluginDependency ->
            applyPlugin(pluginDependency = pluginDependency)
        }
    }

    protected fun Project.applyPlugin(pluginDependency: PluginDependency) {
        getVersionCatalogsPlugin(pluginAlias = pluginDependency.alias).also { plugin ->
            applyPlugin(pluginId = plugin.pluginId)
        }
    }

    private fun PluginAware.applyPlugin(pluginId: String) {
        apply(plugin = pluginId)
    }

    protected fun Project.configureKotlinOptions() {
        tasks.withType(type = KotlinJvmCompile::class) {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_17)
            }
        }
    }

    private fun Project.getVersionCatalogsPlugin(pluginAlias: String) = getVersionCatalogs()
        .findPlugin(pluginAlias)
        .get()
        .get()

    private fun Project.getVersionCatalogs() = extensions
        .getByType<VersionCatalogsExtension>()
        .named(VERSION_CATALOGS_CONTAINER_NAME)

    private companion object {

        private const val VERSION_CATALOGS_CONTAINER_NAME = "libs"

    }

}
