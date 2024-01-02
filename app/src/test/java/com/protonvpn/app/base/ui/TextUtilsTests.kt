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

package com.protonvpn.app.base.ui

import androidx.compose.ui.text.AnnotatedString
import com.protonvpn.android.base.ui.replaceWithInlineContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextUtilsTests {

    @Test
    fun `replace with inline content in the middle`() {
        val result = "text TOKEN text".replaceWithInlineContent("TOKEN", "ID")
        assertEquals("text � text", result.text)
        assertAnnotation("ID", 5, 6, result)
    }

    @Test
    fun `replace with inline content at the beginning`() {
        val result = "TOKEN text".replaceWithInlineContent("TOKEN", "ID")
        assertEquals("� text", result.text)
        assertAnnotation("ID", 0, 1, result)
    }

    @Test
    fun `replace with inline content at the end`() {
        val result = "text TOKEN".replaceWithInlineContent("TOKEN", "ID")
        assertEquals("text �", result.text)
        assertAnnotation("ID", 5, 6, result)
    }

    @Test
    fun `replace with inline content when missing token`() {
        val result = "text".replaceWithInlineContent("TOKEN", "ID")
        assertEquals("text", result.text)
        assertTrue(result.getStringAnnotations(0, result.length).isEmpty())
    }

    private fun assertAnnotation(expectedId: String, expectedStart: Int, expectedEnd: Int, result: AnnotatedString) {
        val annotation = result.getStringAnnotations(0, result.length).first()
        assertEquals(expectedId, annotation.item)
        assertEquals(expectedStart, annotation.start)
        assertEquals(expectedEnd, annotation.end)
    }
}
