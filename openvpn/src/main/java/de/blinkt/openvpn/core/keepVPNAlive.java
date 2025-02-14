/*
 * Copyright (c) 2012-2023 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.core;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.PersistableBundle;

import de.blinkt.openvpn.LaunchVPN;
import de.blinkt.openvpn.VpnProfile;

/**
 * This is a task that is run periodically to restart the VPN if tit has died for
 * some reason in the background
 */
public class keepVPNAlive extends JobService implements VpnStatus.StateListener {
    private ConnectionStatus mLevel = ConnectionStatus.UNKNOWN_LEVEL;
    private static final int JOBID_KEEPVPNALIVE = 6231;

    @Override
    public void onCreate() {
        super.onCreate();
        VpnStatus.addStateListener(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        VpnStatus.removeStateListener(this);
    }

    @Override
    public boolean onStartJob(JobParameters jobParameters) {
        if (mLevel == ConnectionStatus.UNKNOWN_LEVEL || mLevel == ConnectionStatus.LEVEL_NOTCONNECTED) {
            String vpnUUID = jobParameters.getExtras().getString(LaunchVPN.EXTRA_KEY);
            VpnProfile vp = ProfileManager.get(this, vpnUUID);
            if (vp == null) {
                VpnStatus.logError("Keepalive service cannot find VPN");
                unscheduleKeepVPNAliveJobService(this);
                return false;
            }
            VPNLaunchHelper.startOpenVpn(vp, getApplicationContext(), "VPN keep alive Job", false);
        } else {
            VpnStatus.logDebug("Keepalive service called but VPN still connected.");
        }

        /* The job has finished */
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        /* not doing anything */
        return true;
    }

    @Override
    public void updateState(String state, String logmessage,
                            int localizedResId, ConnectionStatus level, Intent Intent) {
        mLevel = level;
    }

    @Override
    public void setConnectedVPN(String uuid) {

    }

    public static void scheduleKeepVPNAliveJobService(Context c, VpnProfile vp) {
        ComponentName keepVPNAliveComponent = new ComponentName(c, keepVPNAlive.class);
        JobInfo.Builder jib = new JobInfo.Builder(JOBID_KEEPVPNALIVE, keepVPNAliveComponent);

        /* set the VPN that should be restarted if we get killed */
        PersistableBundle extraBundle = new PersistableBundle();
        extraBundle.putString(de.blinkt.openvpn.LaunchVPN.EXTRA_KEY, vp.getUUIDString());
        jib.setExtras(extraBundle);

        /* periodic timing */
        /* The current limits are 15 minutes and 5 minutes for flex and periodic timer
         * but we use a minimum of 5 minutes and 2 minutes to avoid problems if there is some
         * strange Android build that allows lower lmits.
         */
        long initervalMillis = Math.max(getMinPeriodMillis(), 5 * 60 * 1000L);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            long flexMillis = Math.max(JobInfo.getMinFlexMillis(), 2 * 60 * 1000L);
            jib.setPeriodic(initervalMillis, flexMillis);
        }
        else
        {
            jib.setPeriodic(initervalMillis);
        }
        jib.setPersisted(true);

        JobScheduler jobScheduler = null;
        jobScheduler = getJobScheduler(c);

        jobScheduler.schedule(jib.build());
        VpnStatus.logDebug("Scheduling VPN keep alive for VPN " + vp.mName);
    }

    private static JobScheduler getJobScheduler(Context c) {
        JobScheduler jobScheduler;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            jobScheduler = c.getSystemService(JobScheduler.class);

        } else {
            jobScheduler = (JobScheduler) c.getSystemService(JOB_SCHEDULER_SERVICE);
        }
        return jobScheduler;
    }

    private static long getMinPeriodMillis() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return JobInfo.getMinPeriodMillis();
        } else {
            return 15 * 60 * 1000L;   // 15 minutes
        }
    }

    public static void unscheduleKeepVPNAliveJobService(Context c) {
        JobScheduler jobScheduler = getJobScheduler(c);
        jobScheduler.cancel(JOBID_KEEPVPNALIVE);
        VpnStatus.logDebug("Unscheduling VPN keep alive");
    }
}
