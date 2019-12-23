/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openpvpn.fragments;

import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;

import de.blinkt.openpvpn.R;
import de.blinkt.openpvpn.core.VpnStatus;

public class SendDumpFragment extends Fragment  {

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		final View v = inflater.inflate(R.layout.fragment_senddump, container, false);
		v.findViewById(R.id.senddump).setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				emailMiniDumps();
			}
		});

		new Thread(new Runnable() {
			@Override
			public void run() {
				final Pair<File, Long> ldump = getLastestDump(getActivity());
				if (ldump==null)
					return;
				// Do in background since it does I/O
				getActivity().runOnUiThread(new Runnable() {
					@Override
					public void run() {
						TextView dumpDateText = (TextView) v.findViewById(R.id.dumpdate);
						String datestr = (new Date(ldump.second)).toString();
                        long timediff = System.currentTimeMillis() - ldump.second;
						long minutes = timediff / 1000 / 60 % 60;
						long hours = timediff / 1000 / 60 / 60;
						dumpDateText.setText(getString(R.string.lastdumpdate, hours, minutes, datestr));

					}
				});
			}
		}).start();
		return v;
	}

	public void emailMiniDumps()
	{
		//need to "send multiple" to get more than one attachment
		final Intent emailIntent = new Intent(android.content.Intent.ACTION_SEND_MULTIPLE);
		emailIntent.setType("*/*");
		emailIntent.putExtra(android.content.Intent.EXTRA_EMAIL, 
				new String[]{"Arne Schwabe <arne@rfc2549.org>"});

		String version;
		String name="ics-openvpn";
		try {
			PackageInfo packageinfo = getActivity().getPackageManager().getPackageInfo(getActivity().getPackageName(), 0);
			version = packageinfo.versionName;
			name = packageinfo.applicationInfo.name;
		} catch (NameNotFoundException e) {
			version = "error fetching version";
		}


		emailIntent.putExtra(Intent.EXTRA_SUBJECT, String.format("%s(%s) %s Minidump",name, getActivity().getPackageName(), version));

		emailIntent.putExtra(Intent.EXTRA_TEXT, "Please describe the issue you have experienced");

		ArrayList<Uri> uris = new ArrayList<>();

		Pair<File, Long> ldump = getLastestDump(getActivity());
		if(ldump==null) {
			VpnStatus.logError("No Minidump found!");
		}

		uris.add(Uri.parse("content://de.blinkt.openpvpn.FileProvider/" + ldump.first.getName()));
		uris.add(Uri.parse("content://de.blinkt.openpvpn.FileProvider/" + ldump.first.getName() + ".log"));

		emailIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
		emailIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
		startActivity(emailIntent);
	}

	static public Pair<File,Long> getLastestDump(Context c) {
		long newestDumpTime=0;
		File newestDumpFile=null;

        if (c.getCacheDir() ==null)
            return null;

		for(File f:c.getCacheDir().listFiles()) {
			if(!f.getName().endsWith(".dmp"))
				continue;

			if (newestDumpTime < f.lastModified()) {
				newestDumpTime = f.lastModified();
				newestDumpFile=f;
			}
		}
		// Ignore old dumps
		if(System.currentTimeMillis() - 48 * 60 * 1000 > newestDumpTime )
		    return null;

		return Pair.create(newestDumpFile, newestDumpTime);
	}
}
