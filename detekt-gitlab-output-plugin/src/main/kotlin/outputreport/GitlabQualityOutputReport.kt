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

package outputreport

import io.gitlab.arturbosch.detekt.api.Detektion
import io.gitlab.arturbosch.detekt.api.Location
import io.gitlab.arturbosch.detekt.api.OutputReport
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

private const val CONTEXT_SIZE = 10

class GitlabQualityOutputReport : OutputReport() {

    private val json = Json { prettyPrint = true }

    override val ending: String = "json"

    override fun render(detektion: Detektion): String {
        val findings = detektion.findings.values.flatten()
        val reports = findings.map {
            val location = with(it.location) {
                GitlabQualityReport.Location(
                    GitlabQualityReport.Location.Lines(source.line, source.line),
                    (filePath.relativePath ?: filePath.absolutePath).toString()
                )
            }
            val fingerprintSignature = if (isFilePosition(it.signature, it.location))
                with(it.location) { getTextContext(filePath.absolutePath.toString(), text.start, text.end) }
            else
                it.signature

            val fingerprint = it.issue.id + " - " + fingerprintSignature
            // Detekt severity doesn't map well to GitLab severity.
            val severity = "info"
            GitlabQualityReport(it.messageOrDescription(), fingerprint, location, severity)
        }
        return json.encodeToString(reports)
    }

    private fun isFilePosition(signature: String, location: Location): Boolean = with(location) {
        signature.endsWith("${filePath.absolutePath.fileName}:${source.line}")
    }

    private fun getTextContext(filePath: String, startOffset: Int, endOffset: Int): String {
        val contents = File(filePath).readText().replace("\r\n", "\n")
        val selection = contents.slice(startOffset until endOffset)
        return selection.ifBlank {
            val preceding = contents.slice((startOffset - CONTEXT_SIZE).coerceAtLeast(0) until startOffset)
            val following = contents.slice(endOffset until (endOffset + CONTEXT_SIZE).coerceAtMost(contents.length))
            preceding + following
        }
    }
}
