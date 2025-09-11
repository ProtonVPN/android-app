package com.protonvpn.android.tv.settings.customdns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TvSettingsCustomDnsViewModel @Inject constructor(
    private val mainScope: CoroutineScope,
    private val userSettingsManager: CurrentUserLocalSettingsManager,
) : ViewModel() {

    sealed interface ViewState {

        data class CustomDns(
            val isCustomDnsEnabled: Boolean,
            val customDnsList: List<String>,
        ) : ViewState {

            val customDnsCount: Int = customDnsList.size

            val hasSingleCustomDns: Boolean = customDnsCount == 1

        }

        data object Empty : ViewState

    }

    val viewStateFlow: StateFlow<ViewState?> = userSettingsManager.rawCurrentUserSettingsFlow
        .map { localUserSettings ->
            if (localUserSettings.customDns.rawDnsList.isEmpty()) {
                ViewState.Empty
            } else {
                ViewState.CustomDns(
                    isCustomDnsEnabled = localUserSettings.customDns.toggleEnabled,
                    customDnsList = localUserSettings.customDns.rawDnsList,
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    fun onToggleIsCustomDnsEnabled() {
        mainScope.launch {
            userSettingsManager.toggleCustomDNS()
        }
    }

}
