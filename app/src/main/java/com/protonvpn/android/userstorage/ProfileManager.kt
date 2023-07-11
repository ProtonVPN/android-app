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

package com.protonvpn.android.userstorage

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.asLiveData
import com.protonvpn.android.appconfig.AppFeaturesPrefs
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.profiles.SavedProfilesV3
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettingsCached
import com.protonvpn.android.utils.Storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileManager @VisibleForTesting constructor(
    private val savedProfiles: SavedProfilesV3,
    private val mainScope: CoroutineScope,
    private val effectiveUserSettings: EffectiveCurrentUserSettingsCached,
    private val userSettingsManager: CurrentUserLocalSettingsManager,
) {

    val profiles = MutableStateFlow(savedProfiles.profileList.toList())
    // TODO: remove the LiveDatas once there is no more Java code using them.
    val profilesLiveData = profiles.asLiveData()
    val fallbackProfile get() = getSavedProfiles().first()

    @Inject
    constructor(
        mainScope: CoroutineScope,
        effectiveUserSettings: EffectiveCurrentUserSettingsCached,
        userSettingsManager: CurrentUserLocalSettingsManager,
        appFeaturesPrefs: AppFeaturesPrefs,
    ) : this(loadProfiles(appFeaturesPrefs), mainScope, effectiveUserSettings, userSettingsManager)

    fun getSavedProfiles(): List<Profile> =
        savedProfiles.profileList

    fun findDefaultProfile(): Profile? = findProfile(effectiveUserSettings.value.defaultProfileId)

    fun findProfile(id: UUID?): Profile? = getSavedProfiles().find { it.id == id }

    fun addToProfileList(profileToSave: Profile?) {
        if (!savedProfiles.profileList.contains(profileToSave)) {
            savedProfiles.profileList.add(profileToSave)
            Storage.save(savedProfiles)
            profiles.value = getSavedProfiles().toList()
        }
    }

    fun editProfile(oldProfile: Profile, profileToSave: Profile) {
        savedProfiles.profileList[savedProfiles.profileList.indexOf(oldProfile)] = profileToSave
        Storage.save(savedProfiles)
        profiles.value = getSavedProfiles().toList()
    }

    fun deleteProfile(profileToSave: Profile?) {
        savedProfiles.profileList.remove(profileToSave)
        Storage.save(savedProfiles)
        profiles.value = getSavedProfiles().toList()
        mainScope.launch {
            userSettingsManager.update { settings ->
                if (settings.defaultProfileId == profileToSave?.id) settings.copy(defaultProfileId = null)
                else settings
            }
        }
    }

    fun deleteSavedProfiles() {
        for (profile in getSavedProfiles().toList()) {
            if (!profile.isPreBakedProfile) {
                deleteProfile(profile)
            }
        }
    }

    companion object {
        private fun loadProfiles(appFeaturesPrefs: AppFeaturesPrefs): SavedProfilesV3 =
            Storage.load(SavedProfilesV3::class.java, SavedProfilesV3.defaultProfiles())
                .migrateProfiles(appFeaturesPrefs)
    }
}
