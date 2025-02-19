/*
 * Copyright (c) 2023. Proton Technologies AG
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

package com.protonvpn.android.vpn.usecases

import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.base.data.FakeVpnFeatureFlag
import com.protonvpn.android.base.data.VpnFeatureFlag
import com.protonvpn.android.base.data.VpnFeatureFlagImpl
import dagger.Reusable
import me.proton.core.featureflag.domain.FeatureFlagManager
import me.proton.core.featureflag.domain.entity.FeatureId
import javax.inject.Inject

interface IsIPv6FeatureFlagEnabled : VpnFeatureFlag

class FakeIsIPv6FeatureFlagEnabled(enabled: Boolean): IsIPv6FeatureFlagEnabled, FakeVpnFeatureFlag(enabled)

@Reusable
class IsIPv6FeatureFlagEnabledImpl @Inject constructor(
    currentUser: CurrentUser,
    featureFlagManager: FeatureFlagManager
): IsIPv6FeatureFlagEnabled, VpnFeatureFlagImpl(currentUser, featureFlagManager, FeatureId("IPv6SupportEnabled"))
