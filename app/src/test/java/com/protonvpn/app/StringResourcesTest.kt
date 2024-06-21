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
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.Locale

// This selection should cover all variants (one, other, few, many etc).
private val PLURAL_QUANTITIES = intArrayOf(0, 1, 2, 4, 5, 7, 12, 16, 30)

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
    private data class PluralResource(val id: Int, val texts: Map<Int, String>, val name: String)

    private val paramRegex = Regex("""%(\d+)\$([0-9.]*[a-z])""")

    @Test
    fun `validate string resources`() {
        val locales = BuildConfig.SUPPORTED_LOCALES
        val app = ApplicationProvider.getApplicationContext<Application>()

        val stringResourcesClass = R.string::class.java
        val pluralsResourcesClass = R.plurals::class.java
        val usResources = resourcesForLocale("us", app)
        val referenceStringParamTypes = getStringResources(stringResourcesClass, usResources)
            .associate { it.id to extractParamTypes(it.text) }
        val referencePluralParamTypes = getPluralResources(pluralsResourcesClass, usResources)
            .associate {
                val pluralParamTypes = it.texts.mapValues { (_, text) -> extractParamTypes(text) }
                it.id to pluralParamTypes
            }

        val errors = locales.map { localeString ->
            try {
                val localizedResources = resourcesForLocale(localeString, app)
                val errors = buildMap {
                    putAll(validateStrings(stringResourcesClass, localizedResources, referenceStringParamTypes))
                    putAll(validatePlurals(pluralsResourcesClass, localizedResources, referencePluralParamTypes))
                }

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

    private fun validatePlurals(
        clazz: Class<*>,
        resources: Resources,
        referencePluralParamTypes: Map<Int, Map<Int, List<String>>>,
    ): Map<String, String> = buildMap {
        getPluralResources(clazz, resources)
            .forEach { plural ->
                val referenceParamTypes = referencePluralParamTypes[plural.id]!!
                plural.texts.forEach { (quantity, text) ->
                    try {
                        val error = validatePlural(plural, text, quantity, resources, referenceParamTypes[quantity])
                        if (error != null) {
                            put("${plural.name} (${quantity})", error)
                        }
                    } catch (e: Throwable) {
                        throw ValidationException("pluralId: ${plural.name} with $quantity", e)
                    }
                }
            }
    }

    private fun validatePlural(
        plural: PluralResource,
        text: String,
        quantity: Int,
        resources: Resources,
        referenceParamTypes: List<String>?,
    ): String? {
        fun error(message: String) = "$message - '${text}'"

        val paramTypes = extractParamTypes(text)

        if (referenceParamTypes != null && !referenceParamTypes.startsWith(paramTypes)) {
            return error("mismatched params, expected $paramTypes to be equal or prefix of $referenceParamTypes")
        }

        try {
            if (paramTypes.isNotEmpty()) {
                resources.getQuantityString(plural.id, quantity, *paramTypes.toValuesArray(quantity))
            }
            return null
        } catch (e: Exception) {
            return error("getQuantityString() throws: $e")
        }
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
        getResources(clazz) { resField ->
            val resId = resField.getInt(clazz)
            StringResource(id = resId, text = resources.getString(resId), name = resField.name)
        }

    private fun getPluralResources(clazz: Class<*>, resources: Resources): Iterable<PluralResource> =
        getResources(clazz) { resField ->
            val resId = resField.getInt(clazz)
            val texts = PLURAL_QUANTITIES.associateWith { quantity -> resources.getQuantityString(resId, quantity) }
            PluralResource(id = resId, texts = texts, name = resField.name)
        }

    private  fun <T> getResources(clazz: Class<*>, extractor: (Field) -> T): Iterable<T> =
        clazz.declaredFields
            .filter { Modifier.isPublic(it.modifiers) && Modifier.isStatic(it.modifiers) }
            .map(extractor)

    private fun resourcesForLocale(localeString: String, appContext: Context): Resources {
        val parts = localeString.split("-r")
        val locale = if (parts.size >= 2) Locale(parts[0], parts[1]) else Locale(parts[0])
        val configuration = Configuration().apply { setLocale(locale) }
        val context = appContext.createConfigurationContext(configuration)
        return context.resources
    }

    private fun Iterable<String>.toValuesArray(intValue: Int = 5) = map { valueForType(it, intValue) }.toTypedArray()

    // Use quantity for int value in plurals to make them less confusing when an error is reported.
    private fun valueForType(typeString: String, intValue: Int): Any = when (typeString) {
        "d" -> intValue
        "s" -> "text"
        "f" -> 1.123f
        else -> throw IllegalArgumentException("Unknown parameter type: $typeString")
    }
}
