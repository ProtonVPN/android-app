/*
 * Copyright (c) 2020 Proton AG
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
import kotlinx.serialization.DeserializationStrategy
import me.proton.core.util.kotlin.deserialize
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

object FileUtils {

    inline fun <reified T> getObjectFromAssetsWithGson(jsonAssetPath: String): T =
        getObjectFromAssets(jsonAssetPath) { json ->
            val listType = object : TypeToken<T>() {}.type
            GsonBuilder().create().fromJson(json, listType)
        }

    inline fun <reified T : Any> getObjectFromAssets(serializer: DeserializationStrategy<T>, jsonAssetPath: String): T =
        getObjectFromAssets(jsonAssetPath) { json ->
            json.deserialize(serializer)
        }

    fun <T> getObjectFromAssets(jsonAssetPath: String, jsonToObject: (String) -> T): T {
        try {
            val file = AssetManager.openFile(jsonAssetPath)
            val size = file.available()
            val buffer = ByteArray(size)
            file.read(buffer)
            val json = buffer.toString(Charsets.UTF_8)
            return jsonToObject(json)
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
            throw e
        }
    }

    fun inputStreamToString(inputStream: InputStream): String? {
        return try {
            val bytes = ByteArray(inputStream.available())
            inputStream.read(bytes, 0, bytes.size)
            String(bytes)
        } catch (e: IOException) {
            null
        }
    }
}
