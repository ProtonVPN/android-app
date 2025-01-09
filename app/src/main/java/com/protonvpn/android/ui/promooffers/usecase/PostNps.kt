/*
 * Copyright (c) 2025. Proton Technologies AG
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
package com.protonvpn.android.ui.promooffers.usecase

import com.protonvpn.android.api.ProtonApiRetroFit
import dagger.Reusable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject

@Reusable
class PostNps @Inject constructor(
    private val mainScope: CoroutineScope,
    private val api: ProtonApiRetroFit,
) {
    operator fun invoke(
        score: Int,
        additionalComment: String = ""
    ) {
        mainScope.launch {
            api.postNps(NpsData(score, additionalComment))
        }
    }

    @Serializable
    class NpsData(
        @SerialName("Score") val score: Int,
        @SerialName("Comment") val comment: String,
    )
}