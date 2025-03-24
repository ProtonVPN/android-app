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
package com.protonvpn.android.ui.promooffers

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.base.ui.TopAppBarCloseIcon
import com.protonvpn.android.base.ui.SimpleTopAppBar
import com.protonvpn.android.base.ui.VpnSolidButton
import com.protonvpn.android.base.ui.theme.VpnTheme
import com.protonvpn.android.redesign.base.ui.ProtonOutlinedTextField
import com.protonvpn.android.redesign.base.ui.largeScreenContentPadding
import dagger.hilt.android.AndroidEntryPoint
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.ProtonTheme3
import me.proton.core.compose.theme.captionWeak
import me.proton.core.compose.theme.defaultNorm

@AndroidEntryPoint
class NpsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            VpnTheme {
                NpsRoute(
                    onClose = { finish() }
                )
            }
        }
    }

    companion object {
        private const val EXTRA_NOTIFICATION_ID = "id"

        @JvmStatic
        fun createIntent(context: Context, notificationId: String) =
            Intent(context, NpsActivity::class.java).apply {
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            }
    }
}

@Composable
fun NpsRoute(
    onClose: () -> Unit
) {
    val activityViewModel: NpsViewModel = hiltViewModel()
    val dismissNps = {
        activityViewModel.dismissNps()
        onClose()
    }

    BackHandler(onBack = dismissNps)
    Surface(
        modifier = Modifier
            .padding(WindowInsets.navigationBars.asPaddingValues())
            .imePadding()
    ) {
        val context = LocalContext.current
        RateMeScreen(
            onClose = dismissNps,
            onSubmit = { score, comment ->
                activityViewModel.postNps(score, comment)
                Toast.makeText(
                    context,
                    R.string.nps_toast_submit_message,
                    Toast.LENGTH_LONG
                ).show()
                onClose()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RateMeScreen(
    onClose: () -> Unit,
    onSubmit: (Int, String) -> Unit
) {
    var selectedRating by rememberSaveable { mutableStateOf<Int?>(null) }
    var additionalDetails by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    var showAdditionalDetails by rememberSaveable { mutableStateOf(false) }
    var inputLimitReached by rememberSaveable { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SimpleTopAppBar(
            title = { },
            isScrolledPredicate = { scrollState.value > 0 },
            navigationIcon = { TopAppBarCloseIcon(onClose) }
        )
        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .weight(1f)
                .padding(horizontal = 16.dp + largeScreenContentPadding(), vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Image(
                painter = painterResource(id = R.drawable.ic_nps),
                contentDescription = null,
                modifier = Modifier
            )
            Text(
                text = stringResource(R.string.nps_how_likely_question),
                style = ProtonTheme.typography.headline,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(24.dp))
            RatingButtons(
                selectedRating = selectedRating,
                onRatingSelected = { newSelection ->
                    selectedRating = newSelection
                    showAdditionalDetails = true
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.nps_rating_low_values_explain),
                    style = ProtonTheme.typography.captionWeak,
                )
                Text(
                    text = stringResource(R.string.nps_rating_high_values_explain),
                    style = ProtonTheme.typography.captionWeak,
                )
            }
            if (showAdditionalDetails) {
                Spacer(modifier = Modifier.height(24.dp))
                ProtonOutlinedTextField(
                    value = additionalDetails,
                    onValueChange = {
                        if (it.text.codePoints().count() <= 250) {
                            additionalDetails = it
                            inputLimitReached = false
                        } else {
                            inputLimitReached = true
                        }
                    },
                    errorText = stringResource(R.string.nps_submit_error_char_limit),
                    isError = inputLimitReached,
                    assistiveText = stringResource(R.string.nps_additional_comment_optional),
                    textStyle = ProtonTheme.typography.defaultNorm,
                    backgroundColor = ProtonTheme.colors.backgroundSecondary,
                    labelText = stringResource(R.string.nps_additional_comment_title),
                    maxLines = 4,
                    textHeightIn = 120.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                )
            }
        }

        VpnSolidButton(
            text = stringResource(R.string.nps_button_submit_title),
            onClick = { onSubmit(selectedRating!!, additionalDetails.text) },
            modifier = Modifier.padding(16.dp).sizeIn(maxWidth = 328.dp),
            enabled = selectedRating != null,
        )
    }
}

@Composable
private fun RatingButtons(
    modifier: Modifier = Modifier,
    selectedRating: Int?,
    onRatingSelected: (Int) -> Unit
) {
    val buttonCount = 11
    val splitIndex = 6

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val parentWidth = maxWidth
        val buttonSpacing = 4.dp
        val minButtonWidth =  48.dp
        val buttonWidthWithSpacing =  minButtonWidth + buttonSpacing
        val totalButtonWidth = buttonWidthWithSpacing * (buttonCount - 1)
        val wideScreenArrangement = totalButtonWidth <= parentWidth
        val buttonModifier = Modifier
            .sizeIn(minWidth = minButtonWidth, minHeight = 44.dp)
        // Show different button arrangement depending on screen width
        if (wideScreenArrangement) {
            // One line with larger width buttons if they fit
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(buttonSpacing)
            ) {
                for (i in 0 until buttonCount) {
                    RatingButton(
                        modifier = buttonModifier.weight(1f),
                        number = i,
                        isSelected = selectedRating == i,
                        onClick = { onRatingSelected(i) }
                    )
                }
            }
        } else {
            // Split in 2 lines if buttons don't fit
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(buttonSpacing, Alignment.CenterHorizontally)
                ) {
                    for (i in 0 until splitIndex) {
                        RatingButton(
                            modifier = buttonModifier,
                            number = i,
                            isSelected = selectedRating == i,
                            onClick = { onRatingSelected(i) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(buttonSpacing, Alignment.CenterHorizontally)
                ) {
                    for (i in splitIndex until buttonCount) {
                        RatingButton(
                            modifier = buttonModifier,
                            number = i,
                            isSelected = selectedRating == i,
                            onClick = { onRatingSelected(i) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RatingButton(modifier: Modifier, number: Int, isSelected: Boolean, onClick: () -> Unit) {
    val backgroundColor =
        if (isSelected) ProtonTheme.colors.interactionNorm else ProtonTheme.colors.interactionWeakNorm

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color = backgroundColor)
            .selectable(
                selected = isSelected,
                role = Role.RadioButton,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = number.toString(),
            style = ProtonTheme.typography.body2Regular
        )
    }
}

@Preview
@Composable
fun PreviewNPS() {
    ProtonVpnPreview {
        RateMeScreen(
            onClose = {},
            onSubmit = { _, _ -> }
        )
    }
}
