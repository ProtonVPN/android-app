package com.protonvpn.android.profiles.ui

import com.protonvpn.android.profiles.data.ProfilesDao
import com.protonvpn.android.profiles.usecases.CreateOrUpdateProfileFromUi
import com.protonvpn.android.redesign.recents.data.toData
import com.protonvpn.android.vpn.VpnStatusProviderUI
import dagger.Reusable
import javax.inject.Inject

@Reusable
class ShouldAskForProfileReconnection @Inject constructor(
    private val vpnStatusProviderUI: VpnStatusProviderUI,
    private val profilesDao: ProfilesDao,
    private val createOrUpdateProfile: CreateOrUpdateProfileFromUi,
) {
    suspend operator fun invoke(
        editedProfileId: Long?,
        nameScreen: NameScreenState,
        typeAndLocationScreen: TypeAndLocationScreenState,
        settingsScreen: SettingsScreenState
    ): Boolean {
        if (editedProfileId == null || vpnStatusProviderUI.connectionIntent?.profileId != editedProfileId) {
            // No reconnection if current connection is not from edited profile
            return false
        }
        val currentProfile = profilesDao.getProfileById(editedProfileId) ?: return false
        val currentIntent = currentProfile.connectIntent.toData()
        val newIntent = createOrUpdateProfile.applyEditsToProfile(
            currentProfile,
            nameScreen,
            typeAndLocationScreen,
            settingsScreen
        ).connectIntent.toData()

        // Make netshield the same as it doesn't require reconnection
        val newIntentWithOldNetShield = newIntent.copy(
            settingsOverrides = newIntent.settingsOverrides?.copy(
                netShield = currentIntent.settingsOverrides?.netShield
            )
        )
        return newIntentWithOldNetShield != currentIntent
    }
}