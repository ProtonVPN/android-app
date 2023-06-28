/*
 * Copyright (c) 2021 Proton Technologies AG
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

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import me.proton.core.network.data.protonApi.IntToBoolSerializer
import me.proton.core.network.domain.client.ClientId
import me.proton.core.network.domain.client.ClientIdType
import me.proton.core.network.domain.client.CookieSessionId
import me.proton.core.network.domain.client.getType
import me.proton.core.network.domain.session.SessionId
import me.proton.core.util.kotlin.toBoolean
import java.lang.reflect.Type

// GSON have a problem with deserializing ClientId class (exception on private constructor)
class ClientIdGsonSerializer : JsonSerializer<ClientId>, JsonDeserializer<ClientId> {
    override fun serialize(src: ClientId, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement =
        JsonObject().apply {
            addProperty("type", src.getType().value)
            addProperty("id", src.id)
        }

    override fun deserialize(json: JsonElement, typeOfT: Type?, context: JsonDeserializationContext?): ClientId {
        val type = ClientIdType.getByValue(json.asJsonObject.get("type").asString)
        val id = json.asJsonObject.get("id").asString
        return when (type) {
            ClientIdType.SESSION -> ClientId.AccountSession(SessionId(id))
            ClientIdType.COOKIE -> ClientId.CookieSession(CookieSessionId(id))
        }
    }
}

object VpnIntToBoolSerializer : KSerializer<Boolean> {

    override val descriptor =
        PrimitiveSerialDescriptor(IntToBoolSerializer::class.qualifiedName!!, PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): Boolean =
        decoder.decodeInt().toBoolean()

    override fun serialize(encoder: Encoder, value: Boolean) {
        encoder.encodeInt(if (value) 1 else 0)
    }
}