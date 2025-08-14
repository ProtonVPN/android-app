package com.protonvpn.android.ui.noconnections

import android.content.Intent
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.AnnotatedClickableText
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.base.ui.VpnSolidButton
import com.protonvpn.android.base.ui.VpnWeakSolidButton
import com.protonvpn.android.redesign.app.ui.ServerLoadingViewModel.LoaderState
import com.protonvpn.android.ui.drawer.bugreport.DynamicReportActivity
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.openUrl
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

            state.helpResId?.let { helpTextId ->
                val linkText = state.helpLinkResId
                    ?.let { helpLinkId -> stringResource(id = helpLinkId) }
                    .orEmpty()

                AnnotatedClickableText(
                    fullText = stringResource(id = helpTextId, linkText),
                    annotatedPart = linkText,
                    annotatedStyle = ProtonTheme.typography.body2Medium,
                    onAnnotatedClick = {
                        when (state.action) {
                            LoaderState.Error.Action.None -> Unit
                            LoaderState.Error.Action.ReportAnIssue -> {
                                context.startActivity(Intent(context, DynamicReportActivity::class.java))
                            }

                            LoaderState.Error.Action.ShowInstructions -> {
                                context.openUrl(url = Constants.URL_ENABLE_VPN_CONNECTION)
                            }
                        }
                    },
                    textAlign = TextAlign.Center,
                    style = ProtonTheme.typography.body2Regular,
                    color = ProtonTheme.colors.textWeak,
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
            LoaderState.Error.DisabledByAdmin,
            LoaderState.Error.NoCountriesNoGateways,
            LoaderState.Error.RequestFailed,
        )

}
