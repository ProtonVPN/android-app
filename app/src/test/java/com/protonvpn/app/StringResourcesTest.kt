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

package com.protonvpn.app

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import com.protonvpn.android.BuildConfig
import com.protonvpn.android.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.lang.reflect.Modifier
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class StringResourcesTest {

    private class ValidationException(
        message: String,
        cause: Throwable
    ) : Exception("$message; ${cause.message}", originalCause(cause)) {
        override fun getStackTrace(): Array<StackTraceElement> = emptyArray()

        companion object {
            private fun originalCause(cause: Throwable) =
                if (cause is ValidationException) cause.cause else cause
        }
    }

    private data class StringResource(val id: Int, val text: String, val name: String)

    private val paramRegex = Regex("""%(\d+)\$([0-9.]*[a-z])""")

    @Test
    fun `validate string resources`() {
        val locales = BuildConfig.SUPPORTED_LOCALES
        val app = ApplicationProvider.getApplicationContext<Application>()

        val resourcesClass = R.string::class.java
        val usResources = resourcesForLocale("us", app)
        val referenceParamTypes = getStringResources(resourcesClass, usResources)
            .associate { it.id to extractParamTypes(it.text) }

        val errors = locales.map { localeString ->
            try {
                val errors = validateStrings(resourcesClass, resourcesForLocale(localeString, app), referenceParamTypes)
                localeString to errors
            } catch (e: Throwable) {
                throw ValidationException("Locale: $localeString", e)
            }
        }
        val errorMessages = errors.flatMap { (locale, stringErrors) ->
            stringErrors.map { (resName, error) ->
                " - $locale: $resName: $error"
            }
        }
        if (errorMessages.isNotEmpty()) {
            throw AssertionError("Found errors in translation strings:\n${errorMessages.joinToString("\n")}")
        }
    }

    private fun validateStrings(
        clazz: Class<*>,
        resources: Resources,
        referenceParamTypes: Map<Int, List<String>>,
    ): Map<String, String> = buildMap {
        getStringResources(clazz, resources)
            .forEach { stringResource ->
                try {
                    val error = validateString(stringResource, resources, referenceParamTypes[stringResource.id])
                    if (error != null)
                        put(stringResource.name, error)
                } catch (e: Throwable) {
                    throw ValidationException("stringId: ${stringResource.name}", e)
                }
            }
    }

    private fun validateString(
        string: StringResource,
        resources: Resources,
        referenceParamTypes: List<String>?,
    ): String? {
        fun error(message: String) = "$message - '${string.text}'"

        val paramTypes = extractParamTypes(string.text)

        if (referenceParamTypes != null && !referenceParamTypes.startsWith(paramTypes)) {
            return error("mismatched params, expected $paramTypes to be equal or prefix of $referenceParamTypes")
        }

        try {
            if (paramTypes.isNotEmpty()) {
                resources.getString(string.id, *paramTypes.toValuesArray())
            }
        } catch (e: Exception) {
            return error("getString() throws: $e")
        }
        return null
    }

    private fun <T> List<T>.startsWith(other: List<T>): Boolean {
        if (size < other.size) return false
        return other.indices.all { this[it] == other[it] }
    }

    private fun extractParamTypes(text: String): List<String> {
        val matches = paramRegex.findAll(text)
        return buildList {
            matches.forEach { match ->
                val (_, positionString, typeString) = match.groupValues

                val index = Integer.parseInt(positionString) - 1
                while (size - 1 < index) add("")
                set(index, typeString)
            }
        }
    }

    private fun getStringResources(clazz: Class<*>, resources: Resources): Iterable<StringResource> =
        clazz.declaredFields
            .filter { Modifier.isPublic(it.modifiers) && Modifier.isStatic(it.modifiers) }
            .map { resField ->
                val resId = resField.getInt(clazz)
                StringResource(id = resId, text = resources.getString(resId), name = resField.name)
            }

    private fun resourcesForLocale(localeString: String, appContext: Context): Resources {
        val parts = localeString.split("-r")
        val locale = if (parts.size >= 2) Locale(parts[0], parts[1]) else Locale(parts[0])
        val configuration = Configuration().apply { setLocale(locale) }
        val context = appContext.createConfigurationContext(configuration)
        return context.resources
    }

    private fun Iterable<String>.toValuesArray() = map { valueForType(it) }.toTypedArray()

    private fun valueForType(typeString: String): Any = when (typeString) {
        "d" -> 5
        "s" -> "text"
        "f" -> 1.123f
        else -> throw IllegalArgumentException("Unknown parameter type: $typeString")
    }
}
