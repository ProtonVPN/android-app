/*
 * Copyright (c) 2023. Proton AG
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

package com.protonvpn.android.ui.onboarding

import com.protonvpn.android.appconfig.AppFeaturesPrefs
import com.protonvpn.android.appconfig.RestrictionsConfig
import com.protonvpn.android.auth.usecase.CurrentUser
import dagger.Reusable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@Reusable
class WhatsNewFreeController @Inject constructor(
    mainScope: CoroutineScope,
    private val appFeaturesPrefs: AppFeaturesPrefs,
    private val restrictionsConfig: RestrictionsConfig,
    currentUser: CurrentUser
) {

    init {
        if (appFeaturesPrefs.showWhatsNew) {
            combine(
                currentUser.vpnUserFlow,
                restrictionsConfig.restrictionFlow.map { it.isRestricted }.distinctUntilChanged()
            ) { vpnUser, isRestricted ->
                // Disable the dialog for paid users, they should not see it when they downgrade to new free UI.
                if (vpnUser?.isUserBasicOrAbove == true && isRestricted)
                    appFeaturesPrefs.showWhatsNew = false
            }.launchIn(mainScope)
        }
    }

    suspend fun initOnLogin() {
        if (restrictionsConfig.restrictionFlow.first().isRestricted)
            appFeaturesPrefs.showWhatsNew = false
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun shouldShowDialog(): Flow<Boolean> = appFeaturesPrefs.showWhatsNewFlow.flatMapLatest { needsDialog ->
        if (needsDialog)
            restrictionsConfig.restrictionFlow.map { it.isRestricted }
        else
            flowOf(false)
    }.distinctUntilChanged()

    fun onDialogShown() {
        appFeaturesPrefs.showWhatsNew = false
    }
}
