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

import com.protonvpn.android.tv.IsTvCheck
import dagger.Module
import dagger.Provides
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ElementsIntoSet
import me.proton.core.eventmanager.domain.EventListener
import me.proton.core.eventmanager.domain.EventManagerConfig
import me.proton.core.eventmanager.domain.entity.Event
import me.proton.core.eventmanager.domain.entity.EventsResponse
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
        isTv: IsTvCheck,
        userEventListener: UserEventListener,
        userAddressEventListener: UserAddressEventListener,
        userSettingsEventListener: UserSettingsEventListener,
        pushEventListener: PushEventListener,
        notificationEventListener: NotificationEventListener,
    ): Set<EventListener<*, *>> = setOf(
        userEventListener,
        MobileOnlyEventListener(userAddressEventListener, isTv),
        MobileOnlyEventListener(userSettingsEventListener, isTv),
        pushEventListener,
        notificationEventListener,
    )
}

// A wrapper for processing events only on non-TV variant of the app, where some endpoints should not be called.
private class MobileOnlyEventListener<ResponseType : Any> constructor(
    private val eventListener: EventListener<String, ResponseType>,
    private val isTv: IsTvCheck
): EventListener<String, ResponseType>() {

    override val order: Int = eventListener.order
    override val type: Type = eventListener.type

    override suspend fun deserializeEvents(
        config: EventManagerConfig,
        response: EventsResponse
    ): List<Event<String, ResponseType>>? =
        when {
            isTv() -> null
            else -> eventListener.deserializeEvents(config, response)
        }

    override suspend fun <R> inTransaction(block: suspend () -> R): R = eventListener.inTransaction(block)
}
