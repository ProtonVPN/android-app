/*
 * Copyright (c) 2025. Proton AG
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

package com.protonvpn.android.servers

import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.base.data.FakeVpnFeatureFlag
import com.protonvpn.android.base.data.VpnFeatureFlag
import com.protonvpn.android.base.data.VpnFeatureFlagImpl
import com.protonvpn.android.vpn.usecases.ServerListTruncationEnabled
import dagger.Reusable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import me.proton.core.featureflag.domain.FeatureFlagManager
import me.proton.core.featureflag.domain.entity.FeatureId
import javax.inject.Inject

// Note: it's not a true deprecation, just an indicator to use the other class instead.
@Deprecated(
    replaceWith = ReplaceWith("IsBinaryServerStatusEnabled"),
    message = "Use IsBinaryServerStatusEnabled instead, it checks more conditions."
)
interface IsBinaryServerStatusFeatureFlagEnabled : VpnFeatureFlag

@Reusable
class IsBinaryServerStatusFeatureFlagEnabledImpl @Inject constructor(
    currentUser: CurrentUser,
    featureFlagManager: FeatureFlagManager,
) : IsBinaryServerStatusFeatureFlagEnabled,
    VpnFeatureFlagImpl(currentUser, featureFlagManager, FeatureId("BinaryServerStatus"))

class FakeIsBinaryServerStatusFeatureFlagEnabled(
    enabled: Flow<Boolean>
) : IsBinaryServerStatusFeatureFlagEnabled, FakeVpnFeatureFlag(enabled) {
    constructor(enabled: Boolean) : this(flowOf(enabled))
}

/**
 * Checks all conditions for enabling binary status feature.
 *
 * The binary status functionality is not compatible with the optimization for free users to refresh paid servers less
 * frequently. Therefore this optimization is disabled when binary loads are enabled.
 * To make things safe, binary status requires that server list truncation is also enabled.
 *
 * To effectively enable binary status two feature flags need to be enabled: BinaryServerStatus and
 * ServerListTruncation.
 */
@Reusable
class IsBinaryServerStatusEnabled @Inject constructor(
    private val isBinaryServerStatusFeatureFlagEnabled: IsBinaryServerStatusFeatureFlagEnabled,
    private val isServerListTruncationFeatureFlagEnabled: ServerListTruncationEnabled
) {
    suspend operator fun invoke() =
        isBinaryServerStatusFeatureFlagEnabled() && isServerListTruncationFeatureFlagEnabled()

    fun observe() = combine(
        isBinaryServerStatusFeatureFlagEnabled.observe(),
        isServerListTruncationFeatureFlagEnabled.observe()
    ) { binaryStatusEnabled, serverListTruncationEnabled ->
        binaryStatusEnabled && serverListTruncationEnabled
    }
}
