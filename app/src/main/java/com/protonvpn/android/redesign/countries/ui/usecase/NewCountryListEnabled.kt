/*
 * Copyright (c) 2024. Proton Technologies AG
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

@file:OptIn(ExperimentalCoroutinesApi::class)

package com.protonvpn.android.redesign.countries.ui.usecase

import com.protonvpn.android.auth.usecase.CurrentUser
import dagger.Reusable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import me.proton.core.featureflag.domain.entity.FeatureId
import me.proton.core.featureflag.domain.repository.FeatureFlagRepository
import me.proton.core.network.domain.ApiException
import javax.inject.Inject

@Reusable
class NewCountryListEnabled @Inject constructor(
    private val currentUser: CurrentUser,
    private val featureFlagRepository: FeatureFlagRepository
) {
    operator fun invoke() = currentUser.vpnUserFlow.flatMapLatest { vpnUser ->
        if (vpnUser == null)
            flowOf(false)
        else {
            featureFlagRepository
                .observe(vpnUser.userId, FeatureId(NEW_COUNTRY_LIST_ENABLED_FLAG), refresh = false)
                .map { flag -> flag?.value ?: false }
                .catch { e ->
                    if (e !is ApiException) throw e
                    emit(false)
                }
        }
    }.distinctUntilChanged()

    companion object {
        const val NEW_COUNTRY_LIST_ENABLED_FLAG = "NewCountryListEnabled"
    }
}
