/*
 * Copyright (c) 2025. Proton AG
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

package com.protonvpn.android.logging

import android.content.Context
import android.content.pm.PackageManager
import java.security.MessageDigest

fun signatureHashes(context: Context): List<String> =
    context.packageManager
        .getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        .signatures
        ?.map { signature -> signatureName(sha1(signature.toByteArray())) } ?: emptyList()

// To be used only for simple hashing in logging
private fun sha1(bytes: ByteArray) =
    // nosemgrep
    MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }

private fun signatureName(hash: String) =
    when (hash) {
        "d8e1ee3ff3a7f6ec46883c898032fe03c23eec20" -> "proton"
        "100f4dec8d194c9985dcd22a7ebd39c91ac9e1ef" -> "fdroid"
        "986388096ce3a46dbe3319b805cf19486c5d359e" -> "amazon"
        "0838993e3ea9c1e3429de8b72e11fb29b2a89bb9" -> "debug"
        else -> "unknown (sha1=$hash)"
    }
