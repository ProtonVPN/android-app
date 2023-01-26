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

package com.protonvpn.android.appconfig.periodicupdates

typealias UpdateActionId = String

/**
 * Represents an action to fetch some data from the backend.
 * The action can have a one input parameter. A default value has to be provided for when the action is executed on a
 * periodic trigger. An explicit value can be provided when executing the action by executeNow().
 *
 * See PeriodicActionResult and examples on PeriodicUpdateManager.
 */
abstract class UpdateAction<T, R : Any> protected constructor(
    val id: UpdateActionId,
    val defaultInput: suspend () -> T
) {
    abstract suspend fun executeWithDefault(): PeriodicActionResult<out R>
    abstract suspend fun execute(input: T): PeriodicActionResult<out R>

    companion object {
        // Create UpdateAction with no input parameters.
        operator fun <R : Any> invoke(
            id: UpdateActionId,
            action: suspend () -> PeriodicActionResult<out R>
        ): UpdateAction<Unit, R> = object : UpdateAction<Unit, R>(id, {}) {
            override suspend fun executeWithDefault(): PeriodicActionResult<out R> = action()
            override suspend fun execute(input: Unit): PeriodicActionResult<out R> = action()
        }

        // Create UpdateAction with one input parameter.
        operator fun <T, R : Any> invoke(
            id: UpdateActionId,
            action: suspend (T) -> PeriodicActionResult<out R>,
            defaultInput: suspend () -> T
        ): UpdateAction<T, R> = object : UpdateAction<T, R>(id, defaultInput) {
            override suspend fun executeWithDefault(): PeriodicActionResult<out R> = action(defaultInput())
            override suspend fun execute(input: T): PeriodicActionResult<out R> = action(input)
        }
    }
}
