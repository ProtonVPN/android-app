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

package com.protonvpn.android.mmp.events.usecases

import android.content.Context
import android.os.Build
import com.protonvpn.android.BuildConfig
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.appconfig.periodicupdates.PeriodicActionResult
import com.protonvpn.android.appconfig.periodicupdates.toPeriodicActionResult
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.mmp.IsMmpFeatureFlagEnabled
import com.protonvpn.android.mmp.events.MmpEvent
import com.protonvpn.android.mmp.events.data.MmpEventRequestBody
import com.protonvpn.android.mmp.events.data.MmpEventResponse
import com.protonvpn.android.mmp.events.data.MmpEventsDao
import com.protonvpn.android.mmp.events.data.toEntities
import com.protonvpn.android.mmp.referrer.MmpReferrer
import com.protonvpn.android.mmp.referrer.data.MmpReferrerStorage
import com.protonvpn.android.mmp.referrer.usecases.GetMmpReferrer
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import me.proton.core.network.domain.ApiResult
import javax.inject.Inject

@Reusable
class SendMmpEvents @Inject constructor(
    private val isMmpEnabled: IsMmpFeatureFlagEnabled,
    private val mmpEventsDao: MmpEventsDao,
    private val getMmpReferrer: GetMmpReferrer,
    private val mmpReferrerStorage: MmpReferrerStorage,
    private val api: ProtonApiRetroFit,
    @param:ApplicationContext private val context: Context,
) {

    suspend operator fun invoke(mmpEvents: List<MmpEvent>): PeriodicActionResult<ApiResult<MmpEventResponse?>> {
        if (!(isMmpEnabled() && mmpEvents.isNotEmpty())) {
            return ApiResult.Success<MmpEventResponse?>(value = null).toPeriodicActionResult()
        }

        val mmpReferrer = getMmpReferrer() ?: return ApiResult.Success<MmpEventResponse?>(value = null).toPeriodicActionResult()

        return withContext(context = NonCancellable) {
            val body = mmpEvents.map { mmpEvent -> createMmpEventBody(mmpReferrer, mmpEvent) }

            val apiResult = api.postMmpEvents(body = body)

            when (apiResult) {
                is ApiResult.Error -> {
                    ProtonLogger.logCustom(
                        category = LogCategory.MMP,
                        message = "Cannot post MMP events: ${apiResult.cause?.message}",
                    )
                }

                is ApiResult.Success<MmpEventResponse> -> {
                    val sessionStartTimestamp = apiResult.value.data.sessionStartMs

                    if (mmpReferrer.sessionStartTimestamp != sessionStartTimestamp) {
                        mmpReferrerStorage.updateMmpReferrer { localMmpReferrer ->
                            localMmpReferrer.copy(sessionStartTimestamp = sessionStartTimestamp)
                        }
                    }

                    mmpEventsDao.delete(entities = mmpEvents.toEntities())
                }
            }

            apiResult.toPeriodicActionResult()
        }
    }

    private fun createMmpEventBody(mmpReferrer: MmpReferrer, mmpEvent: MmpEvent) = MmpEventRequestBody(
        osVersion = Build.VERSION.RELEASE,
        appIdentifier = BuildConfig.APPLICATION_ID,
        appPackageName = context.packageName,
        asid = mmpReferrer.asid,
        eventTimestampMs = mmpEvent.timestamp,
        eventType = mmpEvent.type.value,
        installRef = mmpReferrer.referrerLink,
        isReinstall = false,
        platform = "android",
        sessionStartMs = mmpEvent.sessionStartTimestamp,
        contentList = mmpEvent.subscriptionDetails?.planName?.let(::listOf),
        price = mmpEvent.subscriptionDetails?.price,
        currency = mmpEvent.subscriptionDetails?.currency,
        cycle = mmpEvent.subscriptionDetails?.cycle,
        couponCode = mmpEvent.subscriptionDetails?.couponCode,
        transactionId = mmpEvent.subscriptionDetails?.transactionId,
        isFirstPurchase = mmpEvent.subscriptionDetails?.isFirstPurchase,
        isFreeToPaid = mmpEvent.subscriptionDetails?.isFreeToPaid,
    )

}
