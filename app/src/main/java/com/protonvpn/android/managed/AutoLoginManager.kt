/*
 * Copyright (c) 2024 Proton AG
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

package com.protonvpn.android.managed

import android.content.Intent
import androidx.annotation.StringRes
import com.protonvpn.android.R
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.auth.usecase.Logout
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.managed.usecase.AutoLogin
import com.protonvpn.android.notifications.NotificationHelper
import com.protonvpn.android.redesign.app.ui.CreateLaunchIntent
import com.protonvpn.android.redesign.app.ui.isMainActivity
import com.protonvpn.android.tv.IsTvCheck
import com.protonvpn.android.ui.ForegroundActivityTracker
import com.protonvpn.android.utils.getValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.proton.core.user.domain.entity.User
import javax.inject.Inject
import javax.inject.Singleton

sealed class AutoLoginState {
    data object Disabled : AutoLoginState()
    data object Ongoing : AutoLoginState()
    data object Success : AutoLoginState()
    data class Error(val e: Throwable) : AutoLoginState()
}

@Singleton
class AutoLoginManager @Inject constructor(
    private val mainScope: CoroutineScope,
    private val managedConfig: ManagedConfig,
    lazyAutoLogin: dagger.Lazy<AutoLogin>,
    lazyCurrentUser: dagger.Lazy<CurrentUser>,
    private val lazyForegroundActivityTracker: dagger.Lazy<ForegroundActivityTracker>,
    lazyNotificationHelper: dagger.Lazy<NotificationHelper>,
    lazyResetUiForAutoLogin: dagger.Lazy<ResetUiForAutoLogin>,
    lazyLogout: dagger.Lazy<Logout>,
) {
    private var ongoingLogin : Job? = null
    private val _state = MutableStateFlow<AutoLoginState?>(null)
    private val isInForeground get() = lazyForegroundActivityTracker.get().foregroundActivity != null

    // Note: it's probably better to wrap the login functionality into a separate lazy object that uses non-lazy
    // dependencies.
    private val currentUser by lazyCurrentUser
    private val autoLogin by lazyAutoLogin
    private val notificationHelper by lazyNotificationHelper
    private val resetUiForAutoLogin by lazyResetUiForAutoLogin
    private val logout by lazyLogout

    val state = _state.filterNotNull()

    init {
        mainScope.launch {
            managedConfig
                .collectLatest { config ->
                    if (config == null) {
                        ongoingLogin?.apply {
                            ProtonLogger.logCustom(LogCategory.MANAGED_CONFIG, "null config, cancelling ongoing login")
                            cancelAndJoin()
                        }
                        _state.value = AutoLoginState.Disabled
                    } else {
                        currentUser.partialJointUserFlow.collect { (user, vpnUser, _) ->
                            if (user != null && user.name == config.username) {
                                _state.value = AutoLoginState.Success
                            } else {
                                // This will trigger re-login if user is logged out.
                                login(user, config)
                            }
                        }
                    }
                }
        }
    }

    private fun login(loggedInUser: User?, config: AutoLoginConfig) : Job {
        val oldLogin = ongoingLogin
        ProtonLogger.logCustom(LogCategory.MANAGED_CONFIG, "Initiating auto-login (replacing ongoing = ${oldLogin != null})")
        val job = mainScope.launch {
            notifyIfInBackground(R.string.notification_auto_login_start)
            _state.value = AutoLoginState.Ongoing
            resetUiForAutoLogin.onAutoLoginStarted()
            oldLogin?.cancelAndJoin()
            if (loggedInUser != null) {
                logout()
            }
            val result = autoLogin.execute(config)
            if (result.isSuccess) {
                notifyIfInBackground(R.string.notification_auto_login_success)
                ProtonLogger.logCustom(LogCategory.MANAGED_CONFIG, "Auto-login successful")
            } else {
                result.exceptionOrNull()?.let { error ->
                    notifyIfInBackground(R.string.notification_auto_login_error)
                    ProtonLogger.logCustom(LogCategory.MANAGED_CONFIG, "Auto-login failed: $error")
                    _state.value = AutoLoginState.Error(error)
                }
            }
            ongoingLogin = null
        }
        ongoingLogin = job
        return job
    }

    suspend fun waitForAutoLogin() {
        state.first { it !is AutoLoginState.Ongoing }
    }

    private fun notifyIfInBackground(@StringRes messageRes: Int) {
        if (!isInForeground)
            notificationHelper.showInformationNotification(messageRes)
    }

    fun retry() {
        mainScope.launch {
            val config = managedConfig.value
            if (config == null)
                _state.value = AutoLoginState.Disabled
            else
                login(currentUser.user(), config)
        }
    }
}

// Close other activities on top of our main when auto-login starts.
class ResetUiForAutoLogin @Inject constructor(
    private val mainScope: CoroutineScope,
    private val foregroundActivityTracker: ForegroundActivityTracker,
    private val createLaunchIntent: CreateLaunchIntent,
    private val isTv: IsTvCheck,
) {
    fun onAutoLoginStarted() {
        mainScope.launch {
            val activity = foregroundActivityTracker.foregroundActivityFlow.filterNotNull().first()
            if (!isMainActivity(activity, isTv())) {
                ProtonLogger.logCustom(LogCategory.MANAGED_CONFIG, "Clearing non-main activity: ${activity.localClassName}")
                activity.startActivity(createLaunchIntent.withFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK))
                activity.finish()
            }
        }
    }
}
