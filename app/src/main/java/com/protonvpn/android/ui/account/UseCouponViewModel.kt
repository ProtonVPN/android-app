/*
 * Copyright (c) 2022. Proton AG
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

package com.protonvpn.android.ui.account

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.R
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.utils.DebugUtils
import com.protonvpn.android.utils.UserPlanManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import me.proton.core.network.domain.ApiResult
import javax.inject.Inject

private const val SUBSCRIPTION_CHECK_RETRY_DELAY_MS = 5000L
private const val SUBSCRIPTION_CHECK_RETRY_COUNT = 2

@HiltViewModel
class UseCouponViewModel @Inject constructor(
    private val mainScope: CoroutineScope,
    private val api: ProtonApiRetroFit,
    private val userPlanManager: UserPlanManager,
    private val currentUser: CurrentUser
) : ViewModel() {

    sealed class ViewState {
        object Init : ViewState()
        object Loading : ViewState()
        object SubscriptionUpgraded : ViewState()
        object SuccessButSubscriptionNotUpgradedYet : ViewState()
        class CouponError(val message: String) : ViewState()
        class Error(@StringRes val messageRes: Int) : ViewState()
    }

    val viewState = MutableStateFlow<ViewState>(ViewState.Init)

    fun applyCoupon(couponCode: String) {
        viewModelScope.launch {
            doApplyCoupon(couponCode)
        }
    }

    private suspend fun doApplyCoupon(couponCode: String) {
        viewState.value = ViewState.Loading
        val newState = mainScope.async {
            // Run this operation on the main scope so that it can fully finish even if the user goes back.
            when (val result = api.postPromoCode(couponCode)) {
                is ApiResult.Success<*> -> {
                    // Refresh VPN info. If the subscription isn't updated immediately retry a couple times to give the
                    // backend a bit more time.
                    retry(SUBSCRIPTION_CHECK_RETRY_COUNT, SUBSCRIPTION_CHECK_RETRY_DELAY_MS) {
                        userPlanManager.refreshVpnInfo()
                        currentUser.vpnUser()?.isFreeUser != true
                    }
                    if (currentUser.vpnUser()?.isFreeUser != true) ViewState.SubscriptionUpgraded
                    else ViewState.SuccessButSubscriptionNotUpgradedYet
                }
                is ApiResult.Error.Http -> {
                    val protonData = result.proton
                    if (protonData != null) {
                        ViewState.CouponError(protonData.error)
                    } else {
                        ViewState.Error(R.string.loaderErrorGeneric)
                    }
                }
                is ApiResult.Error.Timeout -> {
                    ViewState.Error(R.string.loaderErrorTimeout)
                }
                is ApiResult.Error.NoInternet -> {
                    ViewState.Error(R.string.loaderErrorNoInternet)
                }
                else -> {
                    ViewState.Error(R.string.loaderErrorGeneric)
                }
            }
        }
        viewState.value = newState.await()
    }

    fun ackErrorState() {
        DebugUtils.debugAssert { viewState.value is ViewState.Error || viewState.value is ViewState.CouponError }
        viewState.value = ViewState.Init
    }

    private suspend fun retry(count: Int, delayMs: Long, action: suspend () -> Boolean) {
        for (i in 0..count) {
            val success = action()
            if (success) break
            if (i < count) delay(delayMs)
        }
    }
}
