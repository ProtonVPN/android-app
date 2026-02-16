/*
 * Copyright (c) 2026. Proton AG
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

package com.protonvpn.android.vpn.protun

import android.content.Context
import com.protonvpn.android.concurrency.VpnDispatcherProvider
import com.protonvpn.android.utils.ifOrNull
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import me.proton.vpn.sdk.api.PacketCaptureFile
import me.proton.vpn.sdk.api.PacketCaptureInfo
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PacketCapture @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatcherProvider: VpnDispatcherProvider,
    private val prefs: PacketCapturePrefs,
) {
    val isCaptureActiveFlow =
        prefs.isActiveFlow.filterNotNull()

    val activeFileFlow: Flow<PacketCaptureInfo?> = combine(
        prefs.isActiveFlow,
        prefs.maxBytesFlow,
    ) { active, maxBytes ->
        ifOrNull(active) {
            context.shareDir().mkdirs()
            PacketCaptureInfo(
                PacketCaptureFile.Path(context.packetCaptureFile(), append = false),
                maxBytes.toULong()
            )
        }
    }.distinctUntilChanged()

    fun setActive(active: Boolean) {
        prefs.isActive = active
    }

    suspend fun fileIfExists() = withContext(dispatcherProvider.Io) {
        context.packetCaptureFile().takeIf { it.exists() }
    }

    suspend fun removeFileIfInactive() = withContext(dispatcherProvider.Io) {
        val active = isCaptureActiveFlow.first()
        if (!active) {
            context.packetCaptureFile().takeIf { it.exists() }?.delete()
        }
    }
}

private fun Context.shareDir() = File(cacheDir, "share")
private fun Context.packetCaptureFile() = File(shareDir(), "protonvpn.pcap")