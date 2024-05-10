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

package com.protonvpn.android.observability

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy
import me.proton.core.observability.domain.metrics.ObservabilityData

// A project-specific subclass of ObservabilityData is needed for the generator.
@Serializable
sealed class VpnObservabilityData : ObservabilityData() {
    @Suppress("UNCHECKED_CAST")
    override val dataSerializer: SerializationStrategy<ObservabilityData>
        get() = serializer() as SerializationStrategy<ObservabilityData>
}
