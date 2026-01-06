/*
 * Copyright (c) 2019 Proton AG
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
package com.protonvpn.android.models.config

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.lang.reflect.Type

@Serializable(with = VpnProtocolSerializer::class)
enum class VpnProtocol {
    WireGuard,
    ProTun,
    Smart;

    fun displayName() = toString()

    val apiName get() = when (this) {
        ProTun -> WireGuard.name // In interactions with API treat ProTun as WireGuard
        else -> name
    }
}

// Serializer that defaults to VpnProtocol.Smart if the value is unrecognized
object VpnProtocolSerializer : KSerializer<VpnProtocol> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("VpnProtocol", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: VpnProtocol) {
        encoder.encodeString(value.name)
    }

    override fun deserialize(decoder: Decoder): VpnProtocol {
        val name = decoder.decodeString()
        return VpnProtocol.entries.find { it.name == name } ?: VpnProtocol.Smart
    }
}

class VpnProtocolGsonSerializer : JsonSerializer<VpnProtocol>, JsonDeserializer<VpnProtocol> {
    override fun serialize(src: VpnProtocol, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        return JsonPrimitive(src.name)
    }

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): VpnProtocol {
        val name = json.asString
        return VpnProtocol.entries.find { it.name == name } ?: VpnProtocol.Smart
    }
}