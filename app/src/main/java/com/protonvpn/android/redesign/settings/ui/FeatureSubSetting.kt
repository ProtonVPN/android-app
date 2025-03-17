/*
 * Copyright (c) 2025. Proton AG
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

package com.protonvpn.android.redesign.settings.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.protonvpn.android.base.ui.SettingsFeatureToggle
import com.protonvpn.android.base.ui.SimpleTopAppBar
import com.protonvpn.android.base.ui.TopAppBarBackIcon
import com.protonvpn.android.redesign.base.ui.ClickableTextAnnotation
import com.protonvpn.android.redesign.base.ui.SettingDescription
import com.protonvpn.android.redesign.base.ui.largeScreenContentPadding
import com.protonvpn.android.redesign.settings.ui.SettingsViewModel.SettingViewState
import me.proton.core.compose.theme.ProtonTheme
import kotlin.math.min

/**
 * A scaffold for a feature screen in settings.
 *
 * @param titleInListIndex Index of item in listState that represents the title text.
 */
@Composable
fun FeatureSubSettingScaffold(
    title: String,
    onClose: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
    titleInListIndex: Int,
    content: @Composable (PaddingValues) -> Unit
) {
    val topAppBarTitleVisibleFraction by remember {
        derivedStateOf { titleVisibilityFraction(listState, titleInListIndex) }
    }
    Scaffold(
        topBar = {
            SimpleTopAppBar(
                title = {
                    if (topAppBarTitleVisibleFraction > 0f) { // Remove invisible text to hide it from accessibility.
                        Text(
                            text = title,
                            color = ProtonTheme.colors.textNorm.copy(alpha = topAppBarTitleVisibleFraction)
                        )
                    }
                },
                isScrolledPredicate = {
                    with(listState) { firstVisibleItemScrollOffset > 0 || firstVisibleItemIndex > 0 }
                },
                navigationIcon = { TopAppBarBackIcon(onClose) }
            )
        },
        content = content
    )
}

@Composable
fun FeatureSubSetting(
    @DrawableRes imageRes: Int,
    onClose: () -> Unit,
    setting: SettingViewState<Boolean>,
    onLearnMore: () -> Unit,
    onToggle: () -> Unit,
) {
    val listState = rememberLazyListState()
    FeatureSubSettingScaffold(
        title = stringResource(id = setting.titleRes),
        listState = listState,
        titleInListIndex = 1,
        onClose = onClose
    ) { contentPadding ->
        val horizontalItemPaddingModifier = Modifier
            .padding(horizontal = 16.dp)
            .largeScreenContentPadding()
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(contentPadding)
        ) {
            addFeatureSettingItems(
                itemModifier = horizontalItemPaddingModifier,
                setting = setting,
                imageRes = imageRes,
                onToggle = onToggle,
                onLearnMore = onLearnMore,
            )
        }
    }
}

fun LazyListScope.addFeatureSettingItems(
    setting: SettingViewState<Boolean>,
    @DrawableRes imageRes: Int,
    onLearnMore: () -> Unit,
    onToggle: () -> Unit,
    itemModifier: Modifier = Modifier,
) {
    addFeatureSettingItems(
        imageRes = imageRes,
        title = { stringResource(setting.titleRes) },
        switchLabel = { stringResource(setting.titleRes) },
        switchValue = { setting.value },
        onSwitchChange = { _ -> onToggle() },
        description = {
            CompositionLocalProvider(
                LocalTextStyle provides ProtonTheme.typography.body2Regular,
                LocalContentColor provides ProtonTheme.colors.textWeak
            ) {
                // TODO: refactor how we handle the embedded links, current Compose version should have better tools.
                SettingDescription(
                    setting.descriptionText(),
                    setting.annotationRes?.let {
                        ClickableTextAnnotation(
                            annotatedPart = stringResource(it),
                            onAnnotatedClick = onLearnMore,
                            onAnnotatedOutsideClick = {}
                        )
                    },
                    modifier = itemModifier.padding(top = 8.dp)
                )
            }
        },
        itemModifier = itemModifier,
    )
}

private fun LazyListScope.addFeatureSettingItems(
    @DrawableRes imageRes: Int,
    title: @Composable () -> String,
    switchLabel: @Composable () -> String,
    switchValue: () -> Boolean,
    onSwitchChange: (Boolean) -> Unit,
    itemModifier: Modifier = Modifier,
    description: @Composable (() -> Unit)? = null,
) {
    item {
        Image(
            painterResource(imageRes),
            contentDescription = null,
            modifier = itemModifier.padding(top = 16.dp)
        )
    }
    item {
        Text(
            title(),
            style = ProtonTheme.typography.subheadline,
            modifier = itemModifier.padding(top = 16.dp)
        )
    }
    if (description != null) {
        item {
            description()
        }
    }

    item {
        SettingsFeatureToggle(
            label = switchLabel(),
            checked = switchValue(),
            onCheckedChange = onSwitchChange,
            modifier = itemModifier.padding(vertical = 16.dp)
        )
    }
}

private fun titleVisibilityFraction(listState: LazyListState, titleItemIndex: Int): Float {
    val itemLayoutInfo = listState.layoutInfo.visibleItemsInfo
        .take(titleItemIndex + 1)
        .firstOrNull { it.index == titleItemIndex }
    return if (itemLayoutInfo != null && itemLayoutInfo.size > 0) {
        val size = itemLayoutInfo.size.toFloat()
        val offset = itemLayoutInfo.offset.toFloat()
        // offset gets negative as the item leaves the top of the screen.
        1f - min(size, size + offset) / size
    } else {
        if (listState.firstVisibleItemIndex == 0) 0f else 1f
    }
}
