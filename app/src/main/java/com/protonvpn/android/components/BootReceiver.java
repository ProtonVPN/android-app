/*
 * Copyright (c) 2017 Proton Technologies AG
 *
 * This file is part of ProtonVPN.
 *
 * ProtonVPN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonVPN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.protonvpn.android.components;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.protonvpn.android.R;
import com.protonvpn.android.models.config.UserData;
import com.protonvpn.android.models.profiles.Profile;
import com.protonvpn.android.models.vpn.Server;
import com.protonvpn.android.ui.LoginActivity;
import com.protonvpn.android.utils.Log;
import com.protonvpn.android.utils.ServerManager;
import com.protonvpn.android.utils.Storage;

import org.strongswan.android.logic.CharonVpnService;

import javax.inject.Inject;

import androidx.core.app.NotificationCompat;
import androidx.core.app.TaskStackBuilder;
import androidx.core.content.ContextCompat;
import dagger.android.AndroidInjection;

public class BootReceiver extends BroadcastReceiver {

    @Inject ServerManager manager;
    @Inject UserData userData;

    @Override
    public void onReceive(Context context, Intent intent) {
        AndroidInjection.inject(this, context);
        if (userData.getConnectOnBoot() && userData.isLoggedIn()) {
            prepareVPN(context, manager, userData);
        }
    }

    public static void prepareVPN(Context context, ServerManager manager, UserData userData) {
        Intent intent;
        try {
            intent = CharonVpnService.prepare(context);
        }
        catch (IllegalStateException ex) {
            Log.exception(ex);
            return;
        }

        if (intent != null) {
            NotificationHelper.INSTANCE.initNotificationChannel(context);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context,
                NotificationHelper.INSTANCE.getCHANNEL_ID()).setSmallIcon(
                R.drawable.ic_notification_disconnected)
                .setContentTitle(context.getString(R.string.insufficientPermissionsTitle))
                .setContentText(context.getString(R.string.insufficientPermissionsDetails));
            Intent resultIntent = new Intent(context, LoginActivity.class);

            TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
            stackBuilder.addParentStack(LoginActivity.class);
            stackBuilder.addNextIntent(resultIntent);
            PendingIntent resultPendingIntent =
                stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
            builder.setContentIntent(resultPendingIntent);
            builder.setAutoCancel(true);
            NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

            notificationManager.notify(1, builder.build());
        }
        else {
            Profile defaultProfile = manager.getDefaultConnection();
            Intent charon = new Intent(context, CharonVpnService.class);
            Server serverToConnect =
                defaultProfile != null ? defaultProfile.getServer() : manager.getBestScoreServerFromAll();
            if (serverToConnect != null) {
                userData.setSecureCoreEnabled(serverToConnect.isSecureCoreServer());
                Storage.save(serverToConnect);
                ContextCompat.startForegroundService(context, charon);
            }
        }
    }
}