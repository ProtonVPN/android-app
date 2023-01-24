/*
 * Copyright (c) 2023 Proton Technologies AG
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
package com.protonvpn.android.netshield

import androidx.annotation.VisibleForTesting
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.config.UserData
import io.sentry.Sentry
import io.sentry.SentryEvent
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetShieldExperiment @Inject constructor(
    private val api: ProtonApiRetroFit,
    private val userData: UserData,
    private val netShieldExperimentPrefs: NetShieldExperimentPrefs,
    private val sentry: SentryRecorder,
    private val currentUser: CurrentUser
) {
    enum class ExperimentState(val value: ExperimentValue) {
        A(ExperimentValue.EXPERIMENT_GROUP_F2),
        D(ExperimentValue.EXPERIMENT_GROUP_F1),
        C(ExperimentValue.CONTROL_GROUP),
        B(ExperimentValue.QUIT_EXPERIMENT)
    }

    enum class ExperimentValue {
        EXPERIMENT_GROUP_F2,
        EXPERIMENT_GROUP_F1,
        CONTROL_GROUP,
        QUIT_EXPERIMENT
    }

    init {
        if (!netShieldExperimentPrefs.experimentEnded) {
            userData.netShieldSettingUpdateEvent.observeForever {
                handleNetShieldChange()
            }
        }
    }

    private fun handleNetShieldChange() {
        // Check if experiment has not ended while already observing
        if (!netShieldExperimentPrefs.experimentEnded) {
            netShieldExperimentPrefs.experimentGroup?.let {
                if (ExperimentState.valueOf(it).value == ExperimentValue.CONTROL_GROUP) {
                    sentry.sendEvent("Netshield A/B: Control group changed value")
                } else {
                    sentry.sendEvent("Netshield A/B: Experiment group changed value")
                }
                netShieldExperimentPrefs.experimentEnded = true
            }
        }
    }

    suspend fun fetchExperiment() {
        if (!netShieldExperimentPrefs.experimentEnded && currentUser.vpnUser()?.isUserPlusOrAbove == true) {
            api.getExperiment("NetShield").valueOrNull?.experiment?.value?.let {
                updateExperiment(it)
            }
        }
    }

    private fun updateExperiment(experimentGroup: ExperimentState) {
        if (!netShieldExperimentPrefs.experimentInitialized || experimentGroup.value == ExperimentValue.QUIT_EXPERIMENT) {
            when (experimentGroup.value) {
                ExperimentValue.EXPERIMENT_GROUP_F2 -> {
                    userData.setNetShieldProtocol(NetShieldProtocol.ENABLED_EXTENDED)
                    sentry.sendEvent("Netshield A/B: Experiment group start")
                }
                ExperimentValue.EXPERIMENT_GROUP_F1 -> {
                    userData.setNetShieldProtocol(NetShieldProtocol.ENABLED)
                    sentry.sendEvent("Netshield A/B: Experiment group start")
                }
                ExperimentValue.CONTROL_GROUP -> {
                    netShieldExperimentPrefs.experimentGroup = experimentGroup.toString()
                    sentry.sendEvent("Netshield A/B: Control group start")
                }
                ExperimentValue.QUIT_EXPERIMENT -> {
                    netShieldExperimentPrefs.experimentEnded = true
                }
            }
            netShieldExperimentPrefs.experimentGroup = experimentGroup.toString()
            netShieldExperimentPrefs.experimentInitialized = true
        }
    }
}

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
class SentryRecorder @Inject constructor() {

    fun sendEvent(message: String) {
        Sentry.captureEvent(SentryEvent(NetShieldExperimentEvent(message)))
    }
}
private class NetShieldExperimentEvent(message: String): Exception(message)