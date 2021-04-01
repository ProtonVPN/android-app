/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import de.blinkt.openvpn.core.Preferences;
import de.blinkt.openvpn.core.ProfileManager;


public class OnBootReceiver extends BroadcastReceiver {



	// Debug: am broadcast -a android.intent.action.BOOT_COMPLETED
	@Override
	public void onReceive(Context context, Intent intent) {

		final String action = intent.getAction();
		SharedPreferences prefs = Preferences.getDefaultSharedPreferences(context);

		boolean useStartOnBoot = prefs.getBoolean("restartvpnonboot", false);
		if (!useStartOnBoot)
			return;

		if(Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
			VpnProfile bootProfile = ProfileManager.getAlwaysOnVPN(context);
			if(bootProfile != null) {
				launchVPN(bootProfile, context);
			}		
		}
	}

	void launchVPN(VpnProfile profile, Context context) {
		Intent startVpnIntent = new Intent(Intent.ACTION_MAIN);
		startVpnIntent.setClass(context, LaunchVPN.class);
		startVpnIntent.putExtra(LaunchVPN.EXTRA_KEY,profile.getUUIDString());
		startVpnIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		startVpnIntent.putExtra(LaunchVPN.EXTRA_HIDELOG, true);

		context.startActivity(startVpnIntent);
	}
}
