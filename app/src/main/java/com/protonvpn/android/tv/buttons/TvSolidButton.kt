package com.protonvpn.android.tv.buttons

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.tv.material3.Text
import com.protonvpn.android.tv.settings.ProtonTvFocusableSurface
import me.proton.core.compose.theme.ProtonTheme
import androidx.compose.material3.ButtonDefaults as CoreButtonDefaults

@Composable
fun TvSolidButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ProtonTvFocusableSurface(
        onClick = onClick,
        color = { ProtonTheme.colors.interactionNorm },
        focusedColor = { ProtonTheme.colors.backgroundNorm },
        shape = ProtonTheme.shapes.medium,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = CoreButtonDefaults.MinHeight),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = ProtonTheme.typography.body1Medium,
                color = ProtonTheme.colors.textNorm,
            )
        }
    }
}
