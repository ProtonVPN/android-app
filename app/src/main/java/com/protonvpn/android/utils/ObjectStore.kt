/*
 * Copyright (c) 2023 Proton AG
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

@file:OptIn(ExperimentalSerializationApi::class)

package com.protonvpn.android.utils

import com.protonvpn.android.concurrency.VpnDispatcherProvider
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.LogLevel
import com.protonvpn.android.logging.ProtonLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.cbor.Cbor
import java.io.File

/** Generic interface for storing and retrieving objects of type [T]. */
interface ObjectStore<T> {
    suspend fun read(): T?
    fun store(data: T)
    fun clear()
}

/** Serializes/deserializes type [T] to [S]. */
interface ObjectSerializer<T, S> {
    fun serialize(data: T): S
    fun deserialize(data: S): T
}

class KotlinCborObjectSerializer<T>(
    private val serializer: KSerializer<T>
) : ObjectSerializer<T, ByteArray> {
    private val cbor = Cbor {
        ignoreUnknownKeys = true
    }

    override fun serialize(data: T): ByteArray = cbor.encodeToByteArray(serializer, data)
    override fun deserialize(data: ByteArray): T = cbor.decodeFromByteArray(serializer, data)
}

/** Writes/reads [S] to/from a file. */
interface FileWriter<S> {
    fun read(file: File): S
    fun write(file: File, data: S)
}

class BytesFileWriter : FileWriter<ByteArray> {
    override fun read(file: File): ByteArray = file.readBytes()
    override fun write(file: File, data: ByteArray) = file.writeBytes(data)
}

// Fast file-backed store for big serializable objects.
class FileObjectStore<T, S> constructor(
    private val storeFile: File,
    val mainScope: CoroutineScope,
    dispatcherProvider: VpnDispatcherProvider,
    private val serializer: ObjectSerializer<T, S>,
    private val fileWriter: FileWriter<S>
) : ObjectStore<T> {

    private val tmpFile = File(storeFile.parent, "${storeFile.name}$TMP_SUFFIX")
    private val ioDispatcher = dispatcherProvider.newSingleThreadDispatcher()

    override suspend fun read(): T? = withContext(ioDispatcher) {
        // If store file doesn't exist, try to read from tmp file, but it might be corrupted - if so
        // we'll return null.
        val file = storeFile.takeIf { it.exists() } ?: tmpFile
        if (file.exists()) {
            try {
                serializer.deserialize(fileWriter.read(file)).also {
                    if (file == tmpFile) {
                        // We successfully read from tmp file, move it to store file
                        tmpFile.renameTo(storeFile)
                    }
                    tmpFile.delete()
                }
            } catch (e: IllegalArgumentException) {
                ProtonLogger.logCustom(LogLevel.ERROR, LogCategory.APP, "Failed to deserialize ${storeFile.name}: ${e.message}")
                null
            }
        } else {
            null
        }
    }

    override fun clear() {
        mainScope.launch(ioDispatcher) {
            tmpFile.delete()
            storeFile.delete()
        }
    }

    override fun store(data: T) {
        // Serialize on current thread, save to file on IO in the background
        val serialized = serializer.serialize(data)
        mainScope.launch(ioDispatcher) {
            if (tmpFile.exists()) {
                tmpFile.delete()
            }
            // Store file is assumed to never be corrupted, tmp might be corrupted if we crash while
            // writing to it.
            fileWriter.write(tmpFile, serialized)
            storeFile.delete()
            tmpFile.renameTo(storeFile)
        }
    }

    companion object {
        const val TMP_SUFFIX = "_tmp"
    }
}
