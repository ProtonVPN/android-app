/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openpvpn.core;

import android.app.Application;
import android.os.Build;
/*
import org.acra.ACRA;
import org.acra.ReportingInteractionMode;
import org.acra.annotation.ReportsCrashes;
*/

import android.os.StrictMode;
import de.blinkt.openpvpn.BuildConfig;
import de.blinkt.openpvpn.api.AppRestrictions;

public class ICSOpenVPNApplication extends Application {
    private StatusListener mStatus;

    @Override
    public void onCreate() {
        if("robolectric".equals(Build.FINGERPRINT))
            return;

        super.onCreate();
        PRNGFixes.apply();
        mStatus = new StatusListener();
        mStatus.init(getApplicationContext());

        if (BuildConfig.BUILD_TYPE.equals("debug"))
            enableStrictModes();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AppRestrictions.getInstance(this).checkRestrictions(this);
        }
    }

    private void enableStrictModes() {
        StrictMode.VmPolicy policy = new StrictMode.VmPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .penaltyDeath()
                .build();
        StrictMode.setVmPolicy(policy);

    }


}
