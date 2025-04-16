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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.navOptions
import com.protonvpn.android.profiles.ui.AddEditProfileRoute
import com.protonvpn.android.profiles.ui.CreateEditProfileViewModel
import com.protonvpn.android.profiles.ui.CreateProfileFeaturesAndSettingsRoute
import com.protonvpn.android.profiles.ui.CreateProfileNameRoute
import com.protonvpn.android.profiles.ui.CreateProfileTypeAndLocationRoute
import com.protonvpn.android.profiles.ui.ProfilesRoute
import com.protonvpn.android.profiles.ui.customdns.ProfileCustomDnsRoute
import com.protonvpn.android.profiles.ui.nav.CreateProfileNameScreen.createProfileName
import com.protonvpn.android.profiles.ui.nav.ProfileCustomDnsScreen.profileCustomDnsScreen
import com.protonvpn.android.profiles.ui.nav.ProfileFeaturesAndSettingsScreen.profileFeaturesAndSettingsScreen
import com.protonvpn.android.profiles.ui.nav.ProfileTypeAndLocationScreen.profileTypeAndLocationScreen
import com.protonvpn.android.redesign.app.ui.nav.RootNav
import com.protonvpn.android.redesign.base.ui.nav.BaseNav
import com.protonvpn.android.redesign.base.ui.nav.NavigationTransition
import com.protonvpn.android.redesign.base.ui.nav.SafeNavGraphBuilder
import com.protonvpn.android.redesign.base.ui.nav.Screen
import com.protonvpn.android.redesign.base.ui.nav.ScreenNoArg
import com.protonvpn.android.redesign.base.ui.nav.addToGraph
import com.protonvpn.android.redesign.base.ui.nav.addToGraphWithSlideAnim
import com.protonvpn.android.redesign.home_screen.ui.ShowcaseRecents
import com.protonvpn.android.redesign.main_screen.ui.nav.MainNav
import kotlinx.serialization.Serializable

object ProfilesScreen : ScreenNoArg<MainNav>("profiles") {

    fun SafeNavGraphBuilder<MainNav>.profiles(
        onNavigateToHomeOnConnect: (ShowcaseRecents) -> Unit,
        onNavigateToAddEdit: (profileId: Long?, duplicate: Boolean) -> Unit,
    ) = addToGraph(this) {
        ProfilesRoute(onNavigateToHomeOnConnect, onNavigateToAddEdit)
    }
}

object AddEditProfileScreen : Screen<AddEditProfileScreen.ProfileCreationArgs, RootNav>("addNewProfile") {

    @Serializable
    data class ProfileCreationArgs(
        val editingProfileId: Long? = null,
        val duplicate: Boolean = false,
        val navigateTo: ProfileCreationTarget? = null,
    )

    fun SafeNavGraphBuilder<RootNav>.addEditProfile(
        onDismiss: () -> Unit,
    ) = addToGraphWithSlideAnim(this) { entry ->
        val profileArgs = AddEditProfileScreen.getArgs<ProfileCreationArgs>(entry)
        AddEditProfileRoute(
            profileArgs.editingProfileId,
            profileArgs.duplicate,
            profileArgs.navigateTo,
            onDismiss
        )
    }
}

object CreateProfileNameScreen : ScreenNoArg<ProfilesAddEditNav>("createProfileName") {

    fun SafeNavGraphBuilder<ProfilesAddEditNav>.createProfileName(
        viewModel: CreateEditProfileViewModel,
        onNext: () -> Unit,
    ) = addToGraph(this) {
        CreateProfileNameRoute(viewModel, onNext = onNext)
    }
}

object ProfileTypeAndLocationScreen : ScreenNoArg<ProfilesAddEditNav>("profileTypeAndLocation") {

    fun SafeNavGraphBuilder<ProfilesAddEditNav>.profileTypeAndLocationScreen(
        viewModel: CreateEditProfileViewModel,
        onNext: () -> Unit,
        onBack: () -> Unit
    ) = addToGraph(this) {
        CreateProfileTypeAndLocationRoute(viewModel, onNext = onNext, onBack = onBack)
    }
}

object ProfileFeaturesAndSettingsScreen : ScreenNoArg<ProfilesAddEditNav>("profileFeaturesAndSettings") {

    fun SafeNavGraphBuilder<ProfilesAddEditNav>.profileFeaturesAndSettingsScreen(
        viewModel: CreateEditProfileViewModel,
        onOpenCustomDns: () -> Unit,
        onNext: () -> Unit,
        onBack: () -> Unit
    ) = addToGraph(this) {
        CreateProfileFeaturesAndSettingsRoute(viewModel, onNext = onNext, onOpenCustomDns = onOpenCustomDns, onBack = onBack)
    }
}

object ProfileCustomDnsScreen : ScreenNoArg<ProfilesAddEditNav>("profileCustomDns") {

    fun SafeNavGraphBuilder<ProfilesAddEditNav>.profileCustomDnsScreen(
        viewModel: CreateEditProfileViewModel,
        onClose: () -> Unit
    ) = addToGraph(this) {
        ProfileCustomDnsRoute(viewModel, onClose)
    }
}

enum class ProfileCreationTarget {
    CreateProfileName,
    TypeAndLocation,
    FeaturesAndSettings;

    val screen get() = when(this) {
        CreateProfileName -> CreateProfileNameScreen
        TypeAndLocation -> ProfileTypeAndLocationScreen
        FeaturesAndSettings -> ProfileFeaturesAndSettingsScreen
    }
}

class ProfilesAddEditNav(
    selfNav: NavHostController,
) : BaseNav<ProfilesAddEditNav>(selfNav, "profilesNav") {

    @Composable
    fun NavHost(
        viewModel: CreateEditProfileViewModel,
        onDone: () -> Unit,
        modifier: Modifier,
        navigateTo: ProfileCreationTarget?,
    ) {
        val navOptions = navOptions {
            launchSingleTop = true
        }
        SafeNavHost(
            modifier = modifier,
            startScreen = CreateProfileNameScreen,
            transition = NavigationTransition.SlideInTowardsStart,
        ) {
            createProfileName(
                viewModel,
                onNext = { navigateInternal(ProfileTypeAndLocationScreen, navOptions) },
            )
            profileTypeAndLocationScreen(
                viewModel,
                onNext = { navigateInternal(ProfileFeaturesAndSettingsScreen, navOptions) },
                onBack = { navigateUpWhenOn(ProfileCreationTarget.TypeAndLocation.screen) }
            )
            profileFeaturesAndSettingsScreen(
                viewModel,
                onNext = onDone,
                onOpenCustomDns = {
                    navigateInternal(ProfileCustomDnsScreen, navOptions)
                },
                onBack = { navigateUpWhenOn(ProfileCreationTarget.FeaturesAndSettings.screen) }
            )
            profileCustomDnsScreen(
                viewModel,
                onClose = { navigateUp() }
            )
        }
        // If we're navigating to a specific step, pre-populate the back stack exactly once
        navigateTo?.let {
            val populatedBackNav = rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(true) {
                if (!populatedBackNav.value) {
                    ProfileCreationTarget.entries.take(navigateTo.ordinal + 1).forEach {
                        navigateInternal(it.screen, navOptions)
                    }
                    populatedBackNav.value = true
                }
            }
        }
    }
}
