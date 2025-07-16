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
import me.proton.core.util.kotlin.equalsNoCase
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.hamcrest.BaseMatcher
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.concurrent.CopyOnWriteArrayList

typealias MockResponseBlock = (RecordedRequest) -> MockResponse
class UnmockedApiCallException(message: String): IllegalArgumentException(message)

class MockRequestDispatcher : Dispatcher() {

    data class Rule(val matcher: Matcher<RecordedRequest>, val responseBlock: MockResponseBlock) {
        fun matches(request: RecordedRequest) = matcher.matches(request)
    }

    private val rules = CopyOnWriteArrayList<Rule>()
    val recordedRequests = CopyOnWriteArrayList<RecordedRequest>()

    fun prependRules(rulesScope: MockRuleBuilder.() -> Unit) = insertRules(prepend = true, rulesScope)

    fun addRules(rulesScope: MockRuleBuilder.() -> Unit) = insertRules(prepend = false, rulesScope)

    private fun insertRules(prepend: Boolean, rulesScope: MockRuleBuilder.() -> Unit) {
        val newRules = mutableListOf<Rule>()
        MockRuleBuilder(newRules).rulesScope()
        if (prepend)
            rules.addAll(0, newRules)
        else
            rules.addAll(newRules)
    }

    override fun dispatch(request: RecordedRequest): MockResponse {
        recordedRequests.add(request)
        // If you get the UnmockedApiCallException it probably means some new API call has been added to the
        // application. This API call needs to be mocked either in TestApiConfig (if it's generic) or in a
        // specific test case that triggers the call.
        val matchingRule = rules.firstOrNull { it.matches(request) }
            ?: throw UnmockedApiCallException("Unmocked call for: ${request.method} ${request.path}")
        return matchingRule.responseBlock(request)
    }
}

fun interface RequestValueGetter {
    fun get(request: RecordedRequest): String?
}

class RequestMatcher(val getter: RequestValueGetter, val matcher: Matcher<String>) : BaseMatcher<RecordedRequest>() {

    override fun describeTo(description: Description) {
        description.appendText("request's property matches").appendDescriptionOf(matcher)
    }

    override fun matches(actual: Any?): Boolean =
        if (actual is RecordedRequest) matcher.matches(getter.get(actual))
        else false
}

class MockRuleBuilder(private val rulesSink: MutableList<MockRequestDispatcher.Rule>) {

    private val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'")

    object Path

    val get = method("GET")
    val head = method("HEAD")
    val post = method("POST")
    val put = method("PUT")
    val delete = method("DELETE")

    val path = Path

    infix fun Path.eq(path: String) = pathMatcher(Matchers.equalTo(path))
    infix fun Path.startsWith(pathPrefix: String) = pathMatcher(Matchers.startsWith(pathPrefix))

    fun rule(vararg allOf: Matcher<RecordedRequest>, responseBlock: MockResponseBlock) {
        rulesSink.add(MockRequestDispatcher.Rule(Matchers.allOf(*allOf), responseBlock))
    }

    fun respond(bodyText: String) = responseWithHeaders(MockResponse().setResponseCode(200).setBody(bodyText))
    fun respond(responseCode: Int) = responseWithHeaders(MockResponse().setResponseCode(responseCode))
    fun respond(responseCode: Int, bodyText: String) =
        responseWithHeaders(MockResponse().setResponseCode(responseCode).setBody(bodyText))
    fun respondBinary(base64Body: String) =
        responseWithHeaders(MockResponse().setResponseCode(200).setBody(base64Body.base64ToBuffer()))
    inline fun <reified T> respond(serializableObject: T) = respond(Json.encodeToString(serializableObject))
    inline fun <reified T> respond(responseCode: Int, serializableObject: T) =
        respond(responseCode, Json.encodeToString(serializableObject))

    private fun responseWithHeaders(mockResponse: MockResponse): MockResponse =
        if (mockResponse.headers.none { it.first.equalsNoCase("Date") })
            mockResponse.addHeader("Date", dateFormat.format(Date()))
        else
            mockResponse

        private fun method(method: String) = RequestMatcher({ request -> request.method }, Matchers.equalTo(method))
    private fun pathMatcher(matcher: Matcher<String>) =
        RequestMatcher({ request -> getPathWithoutQuery(request) }, matcher)

    private fun getPathWithoutQuery(request: RecordedRequest): String? =
        request.path?.split("?")?.firstOrNull()

    private fun String.base64ToBuffer(): Buffer {
        val bytes = Base64.getDecoder().decode(this)
        return Buffer().write(bytes, 0, bytes.size)
    }
}
