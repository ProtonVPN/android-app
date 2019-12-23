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

import android.os.Handler;
import android.os.Looper;

import com.squareup.otto.Bus;
import com.squareup.otto.ThreadEnforcer;

public final class EventBus {

    private final static Bus BUS = new Bus(ThreadEnforcer.ANY);
    private final static Handler MAIN_THREAD = new Handler(Looper.getMainLooper());

    private EventBus() {
    }

    public static Bus getInstance() {
        return BUS;
    }

    public static void postOnMain(final Object event) {
        MAIN_THREAD.post(new Runnable() {
            @Override
            public void run() {
                post(event);
            }
        });
    }

    public static void post(Object object) {
        BUS.post(object);
    }
}