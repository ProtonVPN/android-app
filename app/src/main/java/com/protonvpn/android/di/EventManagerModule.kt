/*
 * Copyright (c) 2020 Proton Technologies AG
 * This file is part of Proton Technologies AG and ProtonCore.
 *
 * ProtonCore is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonCore is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonCore.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.protonvpn.android.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ElementsIntoSet
import me.proton.core.eventmanager.domain.EventListener
import me.proton.core.notification.data.NotificationEventListener
import me.proton.core.push.data.PushEventListener
import me.proton.core.user.data.UserAddressEventListener
import me.proton.core.user.data.UserEventListener
import me.proton.core.usersettings.data.UserSettingsEventListener
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EventManagerModule {

    @Provides
    @Singleton
    @ElementsIntoSet
    @JvmSuppressWildcards
    @Suppress("LongParameterList")
    fun provideEventListenerSet(
        userEventListener: UserEventListener,
        userAddressEventListener: UserAddressEventListener,
        userSettingsEventListener: UserSettingsEventListener,
        pushEventListener: PushEventListener,
        notificationEventListener: NotificationEventListener,
    ): Set<EventListener<*, *>> = setOf(
        userEventListener,
        userAddressEventListener,
        userSettingsEventListener,
        pushEventListener,
        notificationEventListener,
    )
}
