/*
 * Copyright (c) 2019 Proton Technologies AG
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
package com.protonvpn.android.migration

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.google.gson.JsonSyntaxException
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.login.LoginResponse
import com.protonvpn.android.models.profiles.SavedProfilesV3
import com.protonvpn.android.ui.onboarding.OnboardingPreferences
import com.protonvpn.android.utils.AndroidUtils.isPackageSignedWith
import com.protonvpn.android.utils.Log
import com.protonvpn.android.utils.Storage

object NewAppMigrator {

    const val PREFS_MIGRATED_FROM_OLD = "ProtonApplication.PREFS_MIGRATED_FROM_OLD"
    const val OLD_APP_ID = "com.protonvpn.android"

    private const val PREFS_MIGRATION_FINISHED = "ProtonApplication.PREFS_MIGRATION_FINISHED"
    private const val CONTENT_URI_PREFIX = "content://$OLD_APP_ID.content.migration/"
    private const val MIGRATION_PROVIDER_AUTHORITY = "$OLD_APP_ID.content.migration"
    private const val OLD_PACKAGE_CERTIFICATE =
            "308203233082020ba003020102020444fcbebf300d06092a864886f70d01010b050030423110300e060355040a13075465736f6e657431123010060355040b130950726f746f6e56504e311a301806035504031311416c6769726461732050756e647a697573301e170d3137303631363130343535305a170d3432303631303130343535305a30423110300e060355040a13075465736f6e657431123010060355040b130950726f746f6e56504e311a301806035504031311416c6769726461732050756e647a69757330820122300d06092a864886f70d01010105000382010f003082010a028201010086968df72b768bcc8f6f2e022587424f40e7be7d8d4e069e0a2997014a1baf1bc982ce3f4c0ccea8fbd2124a14eab29e26710514872de597c1c24f321634821cdefca2a57c03b0a26b28f857aecbfdfd4f90916f65780f5ac9ff098d6c0559633b139660bf02a970b058252d4d3cce33eda49e68f899336f72d20c4afa66233d7401e81b7e6c69e09edae7ef187d85564f39e25436c32ab781f403ad2d40f2517d20c4bd364b27639a24c88e3ff5c95cc810553da46f4a994256bf117f7c77beaad44d068c8e68adec72070d48eabf53beb94a0a27a363eba3731afaf2a8458cd8e7d0a10055b244fa5604d4de4c40da5d288f4afad435f5fce530f52d4f9eb10203010001a321301f301d0603551d0e0416041426cc2e63f2ff414b85d38f51bce78e2d58f09e82300d06092a864886f70d01010b0500038201010071d5517cfcf5b4cb265f0e3d7e92fc5f45f04a6717a86393b4e1b611a13493d43c11e0e9079b84fa6eee61ed1a75977bbbb36054c19fa05cc734b22286754f79461863ce1e85efaf18936f490212c82d65077e56e5b96d76e90ea28c0dabec2d737817b7129ef9e494f591f57e6e25e9cf8fe86dfbf4a072afeecaf37e8fd37b6a22e9495e1d7a3d4e87f43ff956fd5a04bcc259bfb1545bca5617df22e1664f0f41104ef98786703e025f143dedc520d7d455d7e5649f697de34724d970e710b363b14d9a6eefab60032c76eba32b411ae792c2c6060649545abb7f97bff40bb3b5025dd16ebc27edd7bec7ca8830d66eea71bd9e5bc975c29d125eed2bdbec"

    fun migrate(context: Context) = with(context) {
        if (Storage.getBoolean(PREFS_MIGRATION_FINISHED))
            return

        val providerPackage =
                packageManager.resolveContentProvider(MIGRATION_PROVIDER_AUTHORITY, 0)?.packageName
        if (providerPackage == OLD_APP_ID && isPackageSignedWith(context, providerPackage, OLD_PACKAGE_CERTIFICATE)) {
            Storage.saveBoolean(OnboardingPreferences.SLIDES_SHOWN, true)
            migrateObject(contentResolver, SavedProfilesV3::class.java, "profiles")
            migrateObject(contentResolver, UserData::class.java, "userData")
            migrateObject(contentResolver, LoginResponse::class.java, "loginResponse")
            Storage.saveBoolean(PREFS_MIGRATED_FROM_OLD, true)
        }

        Storage.saveBoolean(PREFS_MIGRATION_FINISHED, true)
    }

    private fun <T> migrateObject(contentResolver: ContentResolver, objClass: Class<T>, name: String) {
        contentResolver.query(
                Uri.parse(CONTENT_URI_PREFIX + name), null, null, null, null
        )?.apply {
            try {
                if (moveToNext())
                    Storage.save(Storage.toObject(objClass, getString(1)))
            } catch (e: Exception) {
                Log.exception(e)
            } catch (e: JsonSyntaxException) {
                Log.exception(e)
            } finally {
                close()
            }
        }
    }
}
