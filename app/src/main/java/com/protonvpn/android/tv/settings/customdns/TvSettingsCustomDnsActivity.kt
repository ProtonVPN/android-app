package com.protonvpn.android.tv.settings.customdns

import android.os.Bundle
import androidx.activity.compose.setContent
import com.protonvpn.android.components.BaseTvActivity
import dagger.hilt.android.AndroidEntryPoint
import me.proton.core.presentation.compose.tv.theme.ProtonThemeTv

@AndroidEntryPoint
class TvSettingsCustomDnsActivity : BaseTvActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ProtonThemeTv {
                // Will be implemented in VPNAND-2352
            }
        }
    }

}
