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

package com.protonvpn.android.profiles.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.protonvpn.android.profiles.ui.AddEditProfileRoute
import com.protonvpn.android.profiles.ui.CreateNameRoute
import com.protonvpn.android.profiles.ui.ProfileFeaturesAndSettingsRoute
import com.protonvpn.android.profiles.ui.ProfileTypeAndLocationRoute
import com.protonvpn.android.profiles.ui.ProfilesRoute
import com.protonvpn.android.profiles.ui.nav.CreateProfileNameScreen.createProfileName
import com.protonvpn.android.profiles.ui.nav.ProfileFeaturesAndSettingsScreen.profileFeaturesAndSettingsScreen
import com.protonvpn.android.profiles.ui.nav.ProfileTypeAndLocationScreen.profileTypeAndLocationScreen
import com.protonvpn.android.redesign.app.ui.nav.RootNav
import com.protonvpn.android.redesign.base.ui.nav.BaseNav
import com.protonvpn.android.redesign.base.ui.nav.SafeNavGraphBuilder
import com.protonvpn.android.redesign.base.ui.nav.ScreenNoArg
import com.protonvpn.android.redesign.base.ui.nav.addToGraph
import com.protonvpn.android.redesign.base.ui.nav.addToGraphWithSlideAnim
import com.protonvpn.android.redesign.home_screen.ui.ShowcaseRecents
import com.protonvpn.android.redesign.main_screen.ui.nav.MainNav

object ProfilesScreen : ScreenNoArg<MainNav>("profiles") {

    fun SafeNavGraphBuilder<MainNav>.profiles(
        onNavigateToHomeOnConnect: (ShowcaseRecents) -> Unit,
        onNavigateToAddNew: () -> Unit,
    ) = addToGraph(this) {
        ProfilesRoute(onNavigateToHomeOnConnect, onNavigateToAddNew)
    }
}

object AddEditProfileScreen : ScreenNoArg<RootNav>("addNewProfile") {

    fun SafeNavGraphBuilder<RootNav>.newProfile(profileId: Int? = null, onBackIconClick: () -> Unit,) = addToGraphWithSlideAnim(this) {
        AddEditProfileRoute(profileId, onBackIconClick)
    }
}

object CreateProfileNameScreen : ScreenNoArg<ProfilesNav>("createProfileName") {

    fun SafeNavGraphBuilder<ProfilesNav>.createProfileName(
        onNext: () -> Unit,
    ) = addToGraph(this) {
        CreateNameRoute(onNext = onNext)
    }
}

object ProfileTypeAndLocationScreen : ScreenNoArg<ProfilesNav>("profileTypeAndLocation") {

    fun SafeNavGraphBuilder<ProfilesNav>.profileTypeAndLocationScreen(
        onNext: () -> Unit,
        onBack: () -> Unit
    ) = addToGraph(this) {
        ProfileTypeAndLocationRoute(onNext = onNext, onBack = onBack)
    }
}

object ProfileFeaturesAndSettingsScreen : ScreenNoArg<ProfilesNav>("profileFeaturesAndSettings") {

    fun SafeNavGraphBuilder<ProfilesNav>.profileFeaturesAndSettingsScreen(
        onNext: () -> Unit,
        onBack: () -> Unit
    ) = addToGraph(this) {
        ProfileFeaturesAndSettingsRoute(onNext = onNext, onBack = onBack)
    }
}
enum class ProfileCreationTarget(val route: String) {
    CreateProfileName(CreateProfileNameScreen.route),
    TypeAndLocation(ProfileTypeAndLocationScreen.route),
    FeaturesAndSettings(ProfileFeaturesAndSettingsScreen.route);
}

class ProfilesNav(
    selfNav: NavHostController,
) : BaseNav<ProfilesNav>(selfNav, "profilesNav") {

    @Composable
    fun NavHost(
        profileId: Int? = null,
        onBackIconClick: () -> Unit,
        modifier: Modifier,
    ) {
        SafeNavHost(
            modifier = modifier,
            startScreen = CreateProfileNameScreen,
        ) {
            createProfileName(
                onNext = { navigateInternal(ProfileTypeAndLocationScreen) },
            )
            profileTypeAndLocationScreen(
                onNext = { navigateInternal(ProfileFeaturesAndSettingsScreen) },
                onBack = { popBackStack() }
            )

            profileFeaturesAndSettingsScreen(
                onNext = onBackIconClick,
                onBack = { popBackStack() }
            )
        }
    }
}
