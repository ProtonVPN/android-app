/*
 * Copyright (c) 2021. Proton Technologies AG
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

package com.protonvpn.android.components

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey

/**
 * A ModelLoader for enabling Glide to load application icons.
 *
 * Example:
 *
 *   val appInfo = packageManager.getApplicationInfo("com.protonvpn.android", 0)
 *   Glide.with(context)
 *       .load(appInfo)
 *       .into(imageView)
 */
class AppIconModelLoader(
    private val packageManager: PackageManager
) : ModelLoader<ApplicationInfo, Drawable> {

    class Factory(
        private val packageManager: PackageManager
    ) : ModelLoaderFactory<ApplicationInfo, Drawable> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<ApplicationInfo, Drawable> =
            AppIconModelLoader(packageManager)


        override fun teardown() {
            // Nothing.
        }
    }

    override fun buildLoadData(
        model: ApplicationInfo,
        width: Int,
        height: Int,
        options: Options
    ) = ModelLoader.LoadData(ObjectKey(model.packageName), AppIconDrawableFetcher(packageManager, model))

    override fun handles(model: ApplicationInfo): Boolean = true
}

private class AppIconDrawableFetcher(
    private val packageManager: PackageManager,
    private val appInfo: ApplicationInfo
) : DataFetcher<Drawable> {

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in Drawable>) {
        callback.onDataReady(
            packageManager.getApplicationIcon(appInfo)
        )
    }

    override fun cleanup() {
        // No resources to release.
    }

    override fun cancel() {
        // Nothing to do.
    }

    override fun getDataClass(): Class<Drawable> = Drawable::class.java

    override fun getDataSource(): DataSource = DataSource.LOCAL
}
