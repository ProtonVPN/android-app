/*
 * Copyright (c) 2024. Proton AG
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

package com.protonvpn.android.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.protonvpn.android.R
import com.protonvpn.android.settings.data.SplitTunnelingMode
import com.protonvpn.android.settings.data.SplitTunnelingSettings
import kotlinx.serialization.descriptors.PrimitiveKind

@Composable
fun formatSplitTunnelingItems(items: List<CharSequence>?): String {
    return when {
        items == null -> "" // Loading state.
        items.isEmpty() -> stringResource(id = R.string.settings_split_tunneling_empty)
        items.size == 1 -> items.first().toString()
        items.size == 2 -> "${items[0]}, ${items[1]}"
        else -> stringResource(
            id = R.string.settings_split_tunneling_excluded_format,
            items[0], items[1], items.size - 2
        )
    }
}
