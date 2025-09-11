package com.protonvpn.android.tv.settings.customdns

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.Text
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseTvActivity
import com.protonvpn.android.tv.buttons.TvSolidButton
import com.protonvpn.android.tv.settings.TvListRow
import com.protonvpn.android.tv.settings.TvSettingsMainToggleLayout
import com.protonvpn.android.tv.settings.customdns.add.TvSettingsAddCustomDnsActivity
import com.protonvpn.android.tv.ui.TvUiConstants
import dagger.hilt.android.AndroidEntryPoint
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.presentation.compose.tv.theme.ProtonThemeTv

@AndroidEntryPoint
class TvSettingsCustomDnsActivity : BaseTvActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ProtonThemeTv {
                val viewModel = hiltViewModel<TvSettingsCustomDnsViewModel>()
                val viewState = viewModel.viewStateFlow.collectAsStateWithLifecycle().value

                when (viewState) {
                    is TvSettingsCustomDnsViewModel.ViewState.CustomDns -> {
                        TvSettingsCustomDns(
                            modifier = Modifier.fillMaxWidth(),
                            viewState = viewState,
                            onAddNewCustomDns = ::openAddCustomDns,
                            onToggled = viewModel::onToggleIsCustomDnsEnabled,
                        )
                    }

                    TvSettingsCustomDnsViewModel.ViewState.Empty -> {
                        TvSettingsCustomDnsEmpty(
                            modifier = Modifier.fillMaxSize(),
                            onAddNewCustomDns = ::openAddCustomDns,
                        )
                    }

                    null -> Unit
                }
            }
        }
    }

    fun openAddCustomDns() {
        val intent = Intent(this, TvSettingsAddCustomDnsActivity::class.java)

        startActivity(intent)
    }

}

@Composable
private fun TvSettingsCustomDnsEmpty(
    onAddNewCustomDns: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(width = 360.dp)
                .padding(horizontal = TvUiConstants.SelectionPaddingHorizontal),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(space = 16.dp),
        ) {
            Image(
                painter = painterResource(id = R.drawable.tv_settings_custom_dns_header_image),
                contentDescription = null,
            )

            Text(
                text = stringResource(id = R.string.settings_custom_dns_title),
                style = ProtonTheme.typography.hero,
            )

            Text(
                text = stringResource(id = R.string.settings_custom_dns_description_tv),
                textAlign = TextAlign.Center,
                style = ProtonTheme.typography.body2Regular,
                color = ProtonTheme.colors.textWeak,
            )

            TvSolidButton(
                modifier = Modifier.padding(top = 16.dp),
                text = stringResource(id = R.string.settings_add_dns_title),
                onClick = onAddNewCustomDns,
            )
        }
    }
}

@Composable
private fun TvSettingsCustomDns(
    viewState: TvSettingsCustomDnsViewModel.ViewState.CustomDns,
    onToggled: () -> Unit,
    onAddNewCustomDns: () -> Unit,
    modifier: Modifier = Modifier
) {
    val customDnsDescriptionResId = remember(key1 = viewState.hasSingleCustomDns) {
        if (viewState.hasSingleCustomDns) R.string.settings_dns_list_description
        else R.string.settings_dns_list_description_multiple
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.TopCenter,
    ) {
        TvSettingsMainToggleLayout(
            modifier = Modifier.widthIn(max = TvUiConstants.SingleColumnWidth),
            title = stringResource(id = R.string.settings_custom_dns_title),
            titleImageRes = R.drawable.tv_settings_custom_dns_header_image,
            toggleLabel = stringResource(id = R.string.settings_custom_dns_title),
            toggleValue = viewState.isCustomDnsEnabled,
            onToggled = onToggled,
        ) {
            item {
                Text(
                    modifier = Modifier
                        .padding(horizontal = TvUiConstants.SelectionPaddingHorizontal)
                        .padding(top = 12.dp, bottom = 16.dp),
                    text = stringResource(id = R.string.settings_custom_dns_description_tv),
                    style = ProtonTheme.typography.body2Regular,
                    color = ProtonTheme.colors.textWeak,
                )
            }

            if (viewState.isCustomDnsEnabled) {
                item {
                    Text(
                        modifier = Modifier
                            .padding(horizontal = TvUiConstants.SelectionPaddingHorizontal)
                            .padding(top = 12.dp, bottom = 16.dp),
                        text = stringResource(
                            id = R.string.settings_dns_list_title,
                            viewState.customDnsCount,
                        ),
                        style = ProtonTheme.typography.body2Medium,
                        color = ProtonTheme.colors.textNorm,
                    )
                }

                items(items = viewState.customDnsList) { customDns ->
                    TvListRow(
                        onClick = {
                            // Will be implemented in VPNAND-2354
                        },
                    ) {
                        Text(
                            text = customDns,
                            style = ProtonTheme.typography.body1Regular,
                            color = ProtonTheme.colors.textNorm,
                        )
                    }
                }

                item {
                    Text(
                        modifier = Modifier
                            .padding(horizontal = TvUiConstants.SelectionPaddingHorizontal)
                            .padding(top = 12.dp, bottom = 16.dp),
                        text = stringResource(id = customDnsDescriptionResId),
                        style = ProtonTheme.typography.body2Regular,
                        color = ProtonTheme.colors.textHint,
                    )
                }

                item {
                    TvSolidButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(id = R.string.settings_add_dns_title),
                        onClick = onAddNewCustomDns,
                    )
                }

                item {
                    Spacer(
                        modifier = Modifier.height(height = 16.dp)
                    )
                }
            }
        }
    }
}
