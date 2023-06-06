/*
 * Copyright (c) 2023. Proton AG
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

import android.content.Context
import com.google.android.play.core.missingsplits.MissingSplitsManagerFactory

// This is relevant for Android versions 6-9. Android 10+ should block installations with missing APKs.
@Suppress("Deprecated")
fun disableIfMissingSplits(context: Context) {
    MissingSplitsManagerFactory.create(context).disableAppIfMissingRequiredSplits()
}
