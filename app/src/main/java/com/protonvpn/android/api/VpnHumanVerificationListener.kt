/*
 * Copyright (c) 2023 Proton AG
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

package com.protonvpn.android.api

import com.protonvpn.android.appconfig.periodicupdates.IsInForeground
import com.protonvpn.android.managed.ManagedConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import me.proton.core.humanverification.data.HumanVerificationListenerImpl
import me.proton.core.humanverification.domain.repository.HumanVerificationRepository
import me.proton.core.network.domain.client.ClientId
import me.proton.core.network.domain.humanverification.HumanVerificationAvailableMethods
import me.proton.core.network.domain.humanverification.HumanVerificationListener
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VpnHumanVerificationListener @Inject constructor(
    humanVerificationRepository: HumanVerificationRepository,
    val guestHole: GuestHole,
    @IsInForeground private val isInForeground: Flow<Boolean>,
    private val managedConfig: ManagedConfig,
) : HumanVerificationListenerImpl(humanVerificationRepository) {

    override suspend fun onHumanVerificationNeeded(
        clientId: ClientId,
        methods: HumanVerificationAvailableMethods
    ): HumanVerificationListener.HumanVerificationResult {
        // If the app is managed and in background user should get notified about the failure and
        // retry in foreground.
        if (managedConfig.isManaged && !isInForeground.first())
            return HumanVerificationListener.HumanVerificationResult.Failure

        guestHole.onBeforeHumanVerification()
        return super.onHumanVerificationNeeded(clientId, methods)
    }
}