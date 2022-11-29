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

package com.protonvpn.detektcustomrules

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.compileAndLint
import org.junit.Assert.assertEquals
import org.junit.Test

internal class MissingInjectScopeTest {

    @Test
    fun `when scope annotation is missing report it`() {
        val code = """
        class Dummy @Inject constructor()
        """
        val findings = MissingInjectScope(Config.empty).compileAndLint(code)
        assertEquals(1, findings.size)
    }

    @Test
    fun `when unknown annotation is present report missing scope annotation`() {
        val code = """
        @SomeAnnotation
        class Dummy @Inject constructor()
        """
        val findings = MissingInjectScope(Config.empty).compileAndLint(code)
        assertEquals(1, findings.size)
    }

    @Test
    fun `when Singleton is present don't report anything`() {
        val code = """
        @Singleton
        class Dummy @Inject constructor()
        """
        val findings = MissingInjectScope(Config.empty).compileAndLint(code)
        assertEquals(0, findings.size)
    }

    @Test
    fun `when Reusable is present don't report anything`() {
        val code = """
        @Reusable
        class Dummy @Inject constructor()
        """
        val findings = MissingInjectScope(Config.empty).compileAndLint(code)
        assertEquals(0, findings.size)
    }

    @Test
    fun `when Distinct is present don't report anything`() {
        val code = """
        @Distinct
        class Dummy @Inject constructor()
        """
        val findings = MissingInjectScope(Config.empty).compileAndLint(code)
        assertEquals(0, findings.size)
    }
}
