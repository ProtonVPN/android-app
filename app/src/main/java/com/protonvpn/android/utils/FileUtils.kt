/*
 * Copyright (c) 2020 Proton Technologies AG
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
package com.protonvpn.android.utils

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.protonvpn.android.ProtonApplication
import java.io.FileNotFoundException

object FileUtils {

    inline fun <reified T> getObjectFromAssets(jsonAssetPath: String): T {
        val manager = ProtonApplication.getAppContext().assets
        try {
            val file = manager.open(jsonAssetPath)
            val size = file.available()
            val buffer = ByteArray(size)
            file.read(buffer)
            val json = buffer.toString(Charsets.UTF_8)
            val listType = object : TypeToken<T>() {}.type
            return GsonBuilder().create().fromJson(json, listType)
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
            throw e
        }
    }
}
