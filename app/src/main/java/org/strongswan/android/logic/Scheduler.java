/*
 * Copyright (C) 2020 Tobias Brunner
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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;

import java.util.ArrayList;
import java.util.PriorityQueue;
import java.util.UUID;

import androidx.annotation.RequiresApi;

public class Scheduler extends BroadcastReceiver
{
	private final String EXECUTE_JOB = "org.strongswan.android.Scheduler.EXECUTE_JOB";
	private final Context mContext;
	private final AlarmManager mManager;
	private final PriorityQueue<ScheduledJob> mJobs;

	public Scheduler(Context context)
	{
		mContext = context;
		mManager = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
		mJobs = new PriorityQueue<>();

		IntentFilter filter = new IntentFilter();
		filter.addAction(EXECUTE_JOB);
		mContext.registerReceiver(this, filter);
	}

	/**
	 * Remove all pending jobs and unregister the receiver.
	 * Called via JNI.
	 */
	public void Terminate()
	{
		synchronized (this)
		{
			mJobs.clear();
		}
		mManager.cancel(createIntent());
		mContext.unregisterReceiver(this);
	}

	/**
	 * Allocate a job ID. Called via JNI.
	 *
	 * @return random ID for a new job
	 */
	public String allocateId()
	{
		return UUID.randomUUID().toString();
	}

	/**
	 * Create a pending intent to execute a job.
	 *
	 * @return pending intent
	 */
	private PendingIntent createIntent()
	{
		/* using component/class doesn't work with dynamic broadcast receivers */
		Intent intent = new Intent(EXECUTE_JOB);
		intent.setPackage(mContext.getPackageName());
		return PendingIntent.getBroadcast(mContext, 0, intent, 0);
	}

	/**
	 * Schedule executing a job in the future.
	 * Called via JNI from different threads.
	 *
	 * @param id job ID
	 * @param ms delta in milliseconds when the job should be executed
	 */
	@RequiresApi(api = Build.VERSION_CODES.M)
	public void scheduleJob(String id, long ms)
	{
		synchronized (this)
		{
			ScheduledJob job = new ScheduledJob(id, System.currentTimeMillis() + ms);
			mJobs.add(job);

			if (job == mJobs.peek())
			{	/* update the alarm if the job has to be executed before all others */
				PendingIntent pending = createIntent();
				mManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, job.Time, pending);
			}
		}
	}

	@RequiresApi(api = Build.VERSION_CODES.M)
	@Override
	public void onReceive(Context context, Intent intent)
	{
		ArrayList<ScheduledJob> jobs = new ArrayList<>();
		long now = System.currentTimeMillis();

		synchronized (this)
		{
			ScheduledJob job = mJobs.peek();
			while (job != null)
			{
				if (job.Time > now)
				{
					break;
				}
				jobs.add(mJobs.remove());
				job = mJobs.peek();
			}
			if (job != null)
			{
				PendingIntent pending = createIntent();
				mManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, job.Time, pending);
			}
		}

		for (ScheduledJob job : jobs)
		{
			executeJob(job.Id);
		}
	}

	/**
	 * Execute the job with the given ID.
	 *
	 * @param id job ID
	 */
	public native void executeJob(String id);

	/**
	 * Keep track of scheduled jobs.
	 */
	private static class ScheduledJob implements Comparable<ScheduledJob>
	{
		String Id;
		long Time;

		ScheduledJob(String id, long time)
		{
			Id = id;
			Time = time;
		}

		@Override
		public int compareTo(ScheduledJob o)
		{
			return Long.compare(Time, o.Time);
		}
	}
}
