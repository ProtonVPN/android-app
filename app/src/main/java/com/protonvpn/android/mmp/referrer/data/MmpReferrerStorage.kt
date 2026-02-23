/*
 * Copyright (c) 2026 Proton AG
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

package com.protonvpn.android.mmp.referrer.data

import com.protonvpn.android.mmp.referrer.MmpReferrer
import com.protonvpn.android.userstorage.JsonDataStoreSerializer
import com.protonvpn.android.userstorage.LocalDataStoreFactory
import dagger.Reusable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first

@Reusable
class MmpReferrerStorage(
    mainScope: CoroutineScope,
    private val localDataStoreFactory: LocalDataStoreFactory,
) {

    private val deferredDataStore = mainScope.async {
        localDataStoreFactory.getDataStore(
            fileName = FILE_NAME,
            serializer = JsonDataStoreSerializer(
                defaultValue = MmpReferrer(
                    asid = "",
                    referrerLink = "",
                ),
                serializer = MmpReferrer.serializer(),
            ),
            migrations = emptyList(),
        )
    }

    suspend fun getMmpReferrer(): MmpReferrer? = getDataStore()
        .data
        .first()
        .takeIf { it.asid.isNotEmpty() && it.referrerLink.isNotEmpty() }

    suspend fun setMmpReferrer(mmpReferrer: MmpReferrer) {
        getDataStore().updateData { mmpReferrer }
    }

    private suspend fun getDataStore() = deferredDataStore.await()

    private companion object {

        private const val FILE_NAME = "mmp_referrer_data_store"

    }

}
