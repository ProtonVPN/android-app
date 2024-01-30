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

package com.protonvpn.mocks

import android.app.PendingIntent
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.Operation
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkContinuation
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import androidx.work.WorkRequest
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * A temporary fake WorkManager implementation for UI tests.
 * See VPNAND-1184 and CP-4334.
 */
class FakeWorkManager : WorkManager() {

    class FakeOperation : Operation {
        override fun getState(): LiveData<Operation.State> =
            MutableLiveData(Operation.SUCCESS)

        override fun getResult(): ListenableFuture<Operation.State.SUCCESS> =
            Futures.immediateFuture(Operation.SUCCESS)
    }

    class FakeWorkContinuation : WorkContinuation() {
        override fun then(work: MutableList<OneTimeWorkRequest>): WorkContinuation {
            TODO("Not yet implemented")
        }

        override fun getWorkInfosLiveData(): LiveData<MutableList<WorkInfo>> {
            TODO("Not yet implemented")
        }

        override fun getWorkInfos(): ListenableFuture<MutableList<WorkInfo>> {
            TODO("Not yet implemented")
        }

        override fun enqueue(): Operation = FakeOperation()

        override fun combineInternal(continuations: MutableList<WorkContinuation>): WorkContinuation {
            TODO("Not yet implemented")
        }
    }

    override fun enqueue(requests: MutableList<out WorkRequest>): Operation = FakeOperation()

    override fun beginWith(work: MutableList<OneTimeWorkRequest>): WorkContinuation = FakeWorkContinuation()

    override fun beginUniqueWork(
        uniqueWorkName: String,
        existingWorkPolicy: ExistingWorkPolicy,
        work: MutableList<OneTimeWorkRequest>
    ): WorkContinuation = FakeWorkContinuation()

    override fun enqueueUniqueWork(
        uniqueWorkName: String,
        existingWorkPolicy: ExistingWorkPolicy,
        work: MutableList<OneTimeWorkRequest>
    ): Operation = FakeOperation()

    override fun enqueueUniquePeriodicWork(
        uniqueWorkName: String,
        existingPeriodicWorkPolicy: ExistingPeriodicWorkPolicy,
        periodicWork: PeriodicWorkRequest
    ): Operation = FakeOperation()

    override fun cancelWorkById(id: UUID): Operation = FakeOperation()

    override fun cancelAllWorkByTag(tag: String): Operation = FakeOperation()

    override fun cancelUniqueWork(uniqueWorkName: String): Operation = FakeOperation()

    override fun cancelAllWork(): Operation = FakeOperation()

    override fun createCancelPendingIntent(id: UUID): PendingIntent {
        TODO("Not yet implemented")
    }

    override fun pruneWork(): Operation = FakeOperation()

    override fun getLastCancelAllTimeMillisLiveData(): LiveData<Long> {
        TODO("Not yet implemented")
    }

    override fun getLastCancelAllTimeMillis(): ListenableFuture<Long> {
        TODO("Not yet implemented")
    }

    override fun getWorkInfoByIdLiveData(id: UUID): LiveData<WorkInfo> {
        TODO("Not yet implemented")
    }

    override fun getWorkInfoByIdFlow(id: UUID): Flow<WorkInfo> {
        TODO("Not yet implemented")
    }

    override fun getWorkInfoById(id: UUID): ListenableFuture<WorkInfo> {
        TODO("Not yet implemented")
    }

    override fun getWorkInfosByTagLiveData(tag: String): LiveData<MutableList<WorkInfo>> {
        TODO("Not yet implemented")
    }

    override fun getWorkInfosByTagFlow(tag: String): Flow<MutableList<WorkInfo>> {
        TODO("Not yet implemented")
    }

    override fun getWorkInfosByTag(tag: String): ListenableFuture<MutableList<WorkInfo>> {
        TODO("Not yet implemented")
    }

    override fun getWorkInfosForUniqueWorkLiveData(uniqueWorkName: String): LiveData<MutableList<WorkInfo>> {
        TODO("Not yet implemented")
    }

    override fun getWorkInfosForUniqueWorkFlow(uniqueWorkName: String): Flow<MutableList<WorkInfo>> {
        TODO("Not yet implemented")
    }

    override fun getWorkInfosForUniqueWork(uniqueWorkName: String): ListenableFuture<MutableList<WorkInfo>> {
        return Futures.immediateFuture(mutableListOf())
    }

    override fun getWorkInfosLiveData(workQuery: WorkQuery): LiveData<MutableList<WorkInfo>> {
        TODO("Not yet implemented")
    }

    override fun getWorkInfosFlow(workQuery: WorkQuery): Flow<MutableList<WorkInfo>> {
        TODO("Not yet implemented")
    }

    override fun getWorkInfos(workQuery: WorkQuery): ListenableFuture<MutableList<WorkInfo>> {
        TODO("Not yet implemented")
    }

    override fun getConfiguration(): Configuration {
        TODO("Not yet implemented")
    }

    override fun updateWork(request: WorkRequest): ListenableFuture<UpdateResult> {
        TODO("Not yet implemented")
    }
}
