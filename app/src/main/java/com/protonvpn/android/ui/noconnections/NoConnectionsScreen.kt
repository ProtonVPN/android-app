package com.protonvpn.android.ui.noconnections

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.base.ui.VpnSolidButton
import com.protonvpn.android.base.ui.VpnWeakSolidButton
import com.protonvpn.android.redesign.app.ui.VpnAppViewModel.LoaderState
import com.protonvpn.android.redesign.reports.FakeIsRedesignedBugReportFeatureFlagEnabled
import com.protonvpn.android.redesign.reports.IsRedesignedBugReportFeatureFlagEnabled
import com.protonvpn.android.redesign.reports.IsRedesignedBugReportFeatureFlagEnabledImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.defaultWeak

@Composable
fun NoConnectionsScreen(
    state: LoaderState.Error,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color = ProtonTheme.colors.backgroundNorm),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(space = 24.dp),
        ) {
            Image(
                painter = painterResource(id = R.drawable.globe_error),
                contentDescription = null,
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(space = 8.dp),
            ) {
                Text(
                    text = stringResource(id = state.titleResId),
                    textAlign = TextAlign.Center,
                    style = ProtonTheme.typography.headline,
                )

                Text(
                    text = stringResource(id = state.descriptionResId),
                    textAlign = TextAlign.Center,
                    style = ProtonTheme.typography.defaultWeak,
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(space = 16.dp),
            ) {
                VpnSolidButton(
                    text = stringResource(id = R.string.no_connections_action_refresh),
                    onClick = onRefresh
                )

                VpnWeakSolidButton(
                    text = stringResource(id = R.string.no_connections_action_sign_out),
                    onClick = onLogout
                )
            }

            if (state.helpResId != null) {
                val helpTextStyle = ProtonTheme.typography.captionRegular
                val helpLinkStyle =  helpTextStyle.copy(color = ProtonTheme.colors.textAccent).toSpanStyle()
                val helpText = stringResource(id = state.helpResId)
                val helpAnnotatedString = if (state.linkAnnotationAction != null) {
                    AnnotatedString.fromHtml(
                        helpText,
                        TextLinkStyles(helpLinkStyle),
                        { state.linkAnnotationAction(context) },
                    )
                } else {
                    AnnotatedString(helpText)
                }

                Text(
                    helpAnnotatedString,
                    color = ProtonTheme.colors.textWeak,
                    style = helpTextStyle,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@ProtonVpnPreview
@Composable
private fun NoConnectionScreenPreview(
    @PreviewParameter(NoConnectionsScreenParameterProvider::class) state: LoaderState.Error,
) {
    ProtonVpnPreview {
        NoConnectionsScreen(
            state = state,
            onRefresh = {},
            onLogout = {},
        )
    }
}

private class NoConnectionsScreenParameterProvider : PreviewParameterProvider<LoaderState.Error> {

    override val values: Sequence<LoaderState.Error>
        get() = sequenceOf(
            LoaderState.Error.DisabledByAdmin {},
            LoaderState.Error.NoCountriesNoGateways {},
            LoaderState.Error.RequestFailed(
                scope = CoroutineScope(context = Dispatchers.Main),
                isRedesignedBugReportFeatureFlagEnabled = FakeIsRedesignedBugReportFeatureFlagEnabled(true),
            ) {},
        )

}
