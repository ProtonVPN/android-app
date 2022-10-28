/*
 * Copyright (c) 2022. Proton AG
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

package com.protonvpn.mocks

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.mock.Behavior
import okhttp3.mock.MockInterceptor
import okhttp3.mock.Rule
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Wraps MockInterceptor to provide functionality for adding rules during tests (which requires thread safety).
 */
class MockInterceptorWrapper : Interceptor {

    private val mockInterceptor = MockInterceptor().apply {
        behavior(Behavior.UNORDERED)
    }
    private val rulesLock = ReentrantReadWriteLock()

    override fun intercept(chain: Interceptor.Chain): Response {
        val mockedResponse = rulesLock.read {
            if (mockInterceptor.rules.isNotEmpty()) mockInterceptor.intercept(chain) else null
        }
        return mockedResponse ?: chain.proceed(chain.request())
    }

    fun addRules(block: MockInterceptor.() -> Unit) {
        rulesLock.write {
            block(mockInterceptor)
        }
    }

    fun prependRules(newRulesBlock: MockInterceptor.() -> Unit) {
        rulesLock.write {
            with (mockInterceptor) {
                val initialRules = rules.toList()
                reset()

                newRulesBlock()

                initialRules.forEach { addRule(it) }
            }
        }
    }

    fun reset() {
        rulesLock.write { mockInterceptor.reset() }
    }
}

inline fun <reified T>Rule.Builder.respond(serializableObject: T): Response.Builder =
    respond(Json.encodeToString(serializableObject))
