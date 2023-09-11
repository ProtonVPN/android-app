package com.protonvpn.android.notifications

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class NotificationPermissionViewModel @Inject constructor(
    private val notificationPrefs: NotificationPermissionPrefs
) : ViewModel() {

    fun doNotShowRationaleAgain() {
        notificationPrefs.rationaleDismissed = true
    }

}