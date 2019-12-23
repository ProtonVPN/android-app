/*
 * Copyright (c) 2012-2015 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openpvpn.activities;

import android.app.Activity;
import android.app.UiModeManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.Window;

public class BaseActivity extends Activity {
    private boolean isAndroidTV() {
        final UiModeManager uiModeManager = (UiModeManager) getSystemService(Activity.UI_MODE_SERVICE);
        return uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (isAndroidTV()) {
            requestWindowFeature(Window.FEATURE_OPTIONS_PANEL);
        }
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }
}
