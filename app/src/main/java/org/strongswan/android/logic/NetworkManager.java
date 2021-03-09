/*
 * Copyright (C) 2012-2019 Tobias Brunner
 * HSR Hochschule fuer Technik Rapperswil
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation; either version 2 of the License, or (at your
 * option) any later version.  See <http://www.fsf.org/copyleft/gpl.txt>.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * for more details.
 */

package org.strongswan.android.logic;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkRequest;
import android.os.Build;

import java.util.LinkedList;

public class NetworkManager extends BroadcastReceiver implements Runnable
{
	private final Context mContext;
	private volatile boolean mRegistered;
	private ConnectivityManager.NetworkCallback mCallback;
	private Thread mEventNotifier;
	private int mConnectedNetworks = 0;
	private LinkedList<Boolean> mEvents = new LinkedList<>();

	public NetworkManager(Context context)
	{
		mContext = context;

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
		{
			mCallback = new ConnectivityManager.NetworkCallback()
			{
				@Override
				public void onAvailable(Network network)
				{
					synchronized (NetworkManager.this)
					{
						/* we expect this to be called if connected to at least one network during
						 * callback registration */
						mConnectedNetworks += 1;
						mEvents.addLast(true);
						NetworkManager.this.notifyAll();
					}
				}

				@Override
				public void onLost(Network network)
				{
					synchronized (NetworkManager.this)
					{
						/* in particular mobile connections are disconnected overlapping with WiFi */
						mConnectedNetworks -= 1;
						mEvents.addLast(mConnectedNetworks > 0);
						NetworkManager.this.notifyAll();
					}
				}
			};
		}
	}

	public void Register()
	{
		mEvents.clear();
		mRegistered = true;
		mEventNotifier = new Thread(this);
		mEventNotifier.start();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
		{
			ConnectivityManager cm = mContext.getSystemService(ConnectivityManager.class);
			/* while we only get events for the VPN network via registerDefaultNetworkCallback,
			 * the default capabilities in the builder include NetworkCapabilities.NET_CAPABILITY_NOT_VPN */
			NetworkRequest.Builder builder = new NetworkRequest.Builder();
			cm.registerNetworkCallback(builder.build(), mCallback);
		}
		else
		{
			registerLegacyReceiver();
		}
	}

	@SuppressWarnings("deprecation")
	private void registerLegacyReceiver()
	{
		/* deprecated since API level 28 */
		mContext.registerReceiver(this, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
	}

	public void Unregister()
	{
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
		{
			ConnectivityManager cm = mContext.getSystemService(ConnectivityManager.class);
			cm.unregisterNetworkCallback(mCallback);
		}
		else
		{
			mContext.unregisterReceiver(this);
		}
		mRegistered = false;
		synchronized (this)
		{
			notifyAll();
		}
		try
		{
			mEventNotifier.join();
			mEventNotifier = null;
		}
		catch (InterruptedException e)
		{
			e.printStackTrace();
		}
	}

	@SuppressWarnings("deprecation")
	public boolean isConnected()
	{
		/* deprecated since API level 29 */
		ConnectivityManager cm = (ConnectivityManager)mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
		android.net.NetworkInfo info = null;
		if (cm != null)
		{
			info = cm.getActiveNetworkInfo();
		}
		return info != null && info.isConnected();
	}

	@Override
	public void onReceive(Context context, Intent intent)
	{
		synchronized (this)
		{
			mEvents.addLast(isConnected());
			notifyAll();
		}
	}

	@Override
	public void run()
	{
		while (mRegistered)
		{
			boolean connected;

			synchronized (this)
			{
				try
				{
					while (mRegistered && mEvents.isEmpty())
					{
						wait();
					}
				}
				catch (InterruptedException ex)
				{
					break;
				}
				if (!mRegistered)
				{
					break;
				}
				connected = mEvents.removeFirst();
			}
			/* call the native parts without holding the lock */
			networkChanged(!connected);
		}
	}

	/**
	 * Notify the native parts about a network change
	 *
	 * @param disconnected true if no connection is available at the moment
	 */
	public native void networkChanged(boolean disconnected);
}
