/*
 * Copyright (c) 2018 Proton Technologies AG
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
package com.protonvpn.testsHelper;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.test.espresso.IdlingResource;

import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;

public final class IdlingResourceHelper implements IdlingResource {

    @CheckResult
    @NonNull
    @SuppressWarnings("ConstantConditions") // Extra guards as a library.
    public static IdlingResourceHelper create(@NonNull String name, @NonNull OkHttpClient client) {
        if (name == null) {
            throw new NullPointerException("name == null");
        }
        if (client == null) {
            throw new NullPointerException("client == null");
        }
        return new IdlingResourceHelper(name, client.dispatcher());
    }

    private final String name;
    private final Dispatcher dispatcher;
    volatile IdlingResource.ResourceCallback callback;

    private IdlingResourceHelper(String name, Dispatcher dispatcher) {
        this.name = name;
        this.dispatcher = dispatcher;
        dispatcher.setIdleCallback(new Runnable() {
            @Override
            public void run() {
                IdlingResource.ResourceCallback callback = IdlingResourceHelper.this.callback;
                if (callback != null) {
                    callback.onTransitionToIdle();
                }
            }
        });
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isIdleNow() {
        boolean idle = (dispatcher.runningCallsCount() == 0);
        if (idle && callback != null) {
            callback.onTransitionToIdle();
        }
        return idle;
    }

    @Override
    public void registerIdleTransitionCallback(IdlingResource.ResourceCallback callback) {
        this.callback = callback;
    }

}
