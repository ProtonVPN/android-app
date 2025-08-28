/*
 * Copyright (c) 2024 Proton AG
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

package com.protonvpn.android.base.data

import com.protonvpn.android.auth.usecase.CurrentUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import me.proton.core.domain.entity.UserId
import me.proton.core.featureflag.domain.ExperimentalProtonFeatureFlag
import me.proton.core.featureflag.domain.FeatureFlagManager
import me.proton.core.featureflag.domain.entity.FeatureId
import me.proton.core.network.domain.ApiException
import javax.inject.Inject

interface VpnFeatureFlag {
    suspend operator fun invoke(): Boolean
    fun observe(): Flow<Boolean>
}

open class FakeVpnFeatureFlag
@Deprecated("Use the constructor with boolean argument and setEnabled function.")
constructor(
    private val enabledFlow: Flow<Boolean>?
) : VpnFeatureFlag {
    constructor(enabled: Boolean) : this(null) {
        setEnabled(enabled)
    }

    private val isEnabled = MutableStateFlow(true)

    fun setEnabled(isEnabled: Boolean) {
        this.isEnabled.value = isEnabled
    }

    override suspend operator fun invoke(): Boolean = enabledFlow?.first() ?: isEnabled.value
    override fun observe(): Flow<Boolean> = enabledFlow ?: isEnabled
}

open class VpnFeatureFlagImpl @Inject constructor(
    private val currentUser: CurrentUser,
    private val featureFlagManager: FeatureFlagManager,
    private val featureId: FeatureId
) : VpnFeatureFlag {
    @OptIn(ExperimentalProtonFeatureFlag::class)
    override suspend operator fun invoke(): Boolean =
        featureFlagManager.getValue(currentUser.user()?.userId, featureId)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe() =
        currentUser.vpnUserFlow.flatMapLatest { vpnUser ->
            featureFlagManager.safeObserve(vpnUser?.userId, featureId)
        }.distinctUntilChanged()
}

private fun FeatureFlagManager.safeObserve(userId: UserId?, featureId: FeatureId) =
    observe(userId, featureId, refresh = false)
        .map { flag -> flag?.value ?: false }
        .catch { e ->
            if (e !is ApiException) throw e
            emit(false)
        }
