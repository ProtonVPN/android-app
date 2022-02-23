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
import io.gitlab.arturbosch.detekt.api.OutputReport
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class GitlabQualityOutputReport : OutputReport() {

    private val json = Json { prettyPrint = true }

    override val ending: String = "json"

    override fun render(detektion: Detektion): String {
        val findings = detektion.findings.values.flatten()
        val reports = findings.map {
            val location = with(it.entity.location) {
                GitlabQualityReport.Location(
                    GitlabQualityReport.Location.Lines(source.line, source.line),
                    (filePath.relativePath ?: filePath.absolutePath).toString()
                )
            }
            val fingerprint = it.issue.id + " - " + it.signature
            // Detekt severity doesn't map well to GitLab severity.
            val severity = "info"
            GitlabQualityReport(it.messageOrDescription(), fingerprint, location, severity)
        }
        return json.encodeToString(reports)
    }
}
