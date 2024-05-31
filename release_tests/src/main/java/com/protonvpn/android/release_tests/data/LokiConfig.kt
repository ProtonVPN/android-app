/*
 *
 *  * Copyright (c) 2023. Proton AG
 *  *
 *  * This file is part of ProtonVPN.
 *  *
 *  * ProtonVPN is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * ProtonVPN is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package com.protonvpn.android.release_tests.data

import com.protonvpn.android.release_tests.BuildConfig
import me.proton.core.test.performance.LogcatFilter
import me.proton.core.test.performance.MeasurementConfig
import java.util.UUID

object LokiConfig {
    val logcatFilter = LogcatFilter()
        .addTag("ProtonLogger")
        .setLokiLogsId(UUID.randomUUID().toString())
        .failTestOnEmptyLogs()

    val measurementConfig = MeasurementConfig
        .setEnvironment("api.protonvpn.ch")
        .setLokiEndpoint(BuildConfig.LOKI_ENDPOINT)
        .setLokiPrivateKey(BuildConfig.LOKI_PRIVATE_KEY_ANDROID)
        .setLokiCertificate(BuildConfig.LOKI_CERTIFICATE_ANDROID)
        .setBuildCommitShortSha(BuildConfig.CI_COMMIT_SHORT_SHA)
}