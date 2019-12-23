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
package com.protonvpn.android.bus;

import com.protonvpn.android.utils.ConnectionTools;

public class TrafficUpdate {

    private final long downloadSpeed;
    private final long uploadSpeed;
    private final long sessionDownload;
    private final long sessionUpload;
    private final int sessionTime;

    public TrafficUpdate(long downloadeSpeed, long uploadSpeed, long sessionDownload, long sessionUpload,
                         int sessionTime) {

        this.downloadSpeed = downloadeSpeed;
        this.uploadSpeed = uploadSpeed;
        this.sessionDownload = sessionDownload;
        this.sessionUpload = sessionUpload;
        this.sessionTime = sessionTime;
    }

    public String getNotificationString() {
        return "↓ " + getSessionDownloadString() + " | " + getDownloadSpeedString() + " ↑ "
            + getSessionUploadString() + " | " + getUploadSpeedString();
    }

    public String getDownloadSpeedString() {
        return ConnectionTools.bytesToSize(downloadSpeed) + "/s";
    }

    public String getUploadSpeedString() {
        return ConnectionTools.bytesToSize(uploadSpeed) + "/s";
    }

    public String getSessionDownloadString() {
        return ConnectionTools.bytesToSize(sessionDownload);
    }

    public String getSessionUploadString() {
        return ConnectionTools.bytesToSize(sessionUpload);
    }

    public long getDownloadSpeed() {
        return downloadSpeed;
    }

    public long getUploadSpeed() {
        return uploadSpeed;
    }

    public long getSessionDownload() {
        return sessionDownload;
    }

    public long getSessionUpload() {
        return sessionUpload;
    }

    public int getSessionTime() {
        return sessionTime;
    }
}
