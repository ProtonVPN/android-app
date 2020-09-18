/*
 * Copyright (C) 2012-2013 Tobias Brunner
 * Hochschule fuer Technik Rapperswil
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

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.SystemClock;

import com.protonvpn.android.R;
import com.protonvpn.android.utils.Log;

import org.strongswan.android.data.VpnProfile;
import org.strongswan.android.logic.imc.ImcState;
import org.strongswan.android.logic.imc.RemediationInstruction;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;

import androidx.core.content.ContextCompat;

public class VpnStateService extends Service {

    private final HashSet<VpnStateListener> mListeners = new HashSet<VpnStateListener>();
    private final IBinder mBinder = new LocalBinder();
    private long mConnectionID = 0;
    private Handler mHandler;
    private VpnProfile mProfile;
    private State mState = State.DISABLED;
    private ErrorState mError = ErrorState.NO_ERROR;
    private ImcState mImcState = ImcState.UNKNOWN;
    private final LinkedList<RemediationInstruction> mRemediationInstructions = new LinkedList<>();
    private static long RETRY_INTERVAL = 1000;
    private static long MAX_RETRY_INTERVAL = 120000;
    private static int RETRY_MSG = 1;
    private RetryTimeoutProvider timeoutProvider = new RetryTimeoutProvider();
    private long retryTimeout;
    private long retryIn;

    public enum State {
        DISABLED, CHECKING_AVAILABILITY, WAITING_FOR_NETWORK, CONNECTING, CONNECTED, RECONNECTING, DISCONNECTING, ERROR
    }

    public enum ErrorState {
        NO_ERROR, AUTH_FAILED, PEER_AUTH_FAILED, LOOKUP_FAILED, UNREACHABLE, SESSION_IN_USE, MAX_SESSIONS,
        GENERIC_ERROR, MULTI_USER_PERMISSION
    }

    /**
     * Listener interface for bound clients that are interested in changes to
     * this Service.
     */
    public interface VpnStateListener {

        void stateChanged();
    }

    /**
     * Simple Binder that allows to directly access this Service class itself
     * after binding to it.
     */
    public class LocalBinder extends Binder {

        public VpnStateService getService() {
            return VpnStateService.this;
        }
    }

    @Override
    public void onCreate() {
        /* this handler allows us to notify listeners from the UI thread and
         * not from the threads that actually report any state changes */
        mHandler = new RetryHandler(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy() {
    }

    /**
     * Register a listener with this Service. We assume this is called from
     * the main thread so no synchronization is happening.
     *
     * @param listener listener to register
     */
    public void registerListener(VpnStateListener listener) {
        mListeners.add(listener);
    }

    /**
     * Unregister a listener from this Service.
     *
     * @param listener listener to unregister
     */
    public void unregisterListener(VpnStateListener listener) {
        mListeners.remove(listener);
    }

    /**
     * Get the current VPN profile.
     *
     * @return profile
     */
    public VpnProfile getProfile() {    /* only updated from the main thread so no synchronization needed */
        return mProfile;
    }

    /**
     * Get the current connection ID.  May be used to track which state
     * changes have already been handled.
     * <p>
     * Is increased when startConnection() is called.
     *
     * @return connection ID
     */
    public long getConnectionID() {    /* only updated from the main thread so no synchronization needed */
        return mConnectionID;
    }

    public int getRetryTimeout() {
        return (int) (retryTimeout / 1000);
    }

    public int getRetryIn() {
        return (int) (retryIn / 1000);
    }

    /**
     * Get the current state.
     *
     * @return state
     */
    public State getState() {    /* only updated from the main thread so no synchronization needed */
        return mState;
    }

    /**
     * Get the current error, if any.
     *
     * @return error
     */
    public ErrorState getErrorState() {    /* only updated from the main thread so no synchronization
    needed */
        return mError;
    }

    public int getErrorText() {
        switch (mError) {
            case AUTH_FAILED:
                if (mImcState == ImcState.BLOCK) {
                    return R.string.error_assessment_failed;
                }
                else {
                    return R.string.error_auth_failed;
                }
            case PEER_AUTH_FAILED:
                return R.string.error_peer_auth_failed;
            case LOOKUP_FAILED:
                return R.string.error_lookup_failed;
            case UNREACHABLE:
                return R.string.error_unreachable;
            default:
                return R.string.error_generic;
        }
    }

    /**
     * Get the current IMC state, if any.
     *
     * @return imc state
     */
    public ImcState getImcState() {    /* only updated from the main thread so no synchronization needed */
        return mImcState;
    }

    /**
     * Get the remediation instructions, if any.
     *
     * @return read-only list of instructions
     */
    public List<RemediationInstruction> getRemediationInstructions() {
        /* only updated from the main thread so no synchronization needed */
        return Collections.unmodifiableList(mRemediationInstructions);
    }

    /**
     * Disconnect any existing connection and shutdown the daemon, the
     * VpnService is not stopped but it is reset so new connections can be
     * started.
     */
    public void disconnect() {
        /* reset any potential retry timer and error state */
        resetRetryTimer();
        setError(ErrorState.NO_ERROR);

        /* as soon as the TUN device is created by calling establish() on the
         * VpnService.Builder object the system binds to the service and keeps
         * bound until the file descriptor of the TUN device is closed.  thus
         * calling stopService() here would not stop (destroy) the service yet,
         * instead we call startService() with an empty Intent which shuts down
         * the daemon (and closes the TUN device, if any) */
        Context context = getApplicationContext();
        Intent intent = new Intent(context, CharonVpnService.class);
        intent.setAction(CharonVpnService.DISCONNECT_ACTION);
        context.startService(intent);
    }

    public void connect(Bundle profileInfo, boolean fromScratch) {
        Context context = getApplicationContext();
        Intent intent = new Intent(context, CharonVpnService.class);
        if (profileInfo == null) {
            profileInfo = new Bundle();
        }
        if (fromScratch) {
            timeoutProvider.reset();
        }
        else {
            setError(VpnStateService.ErrorState.NO_ERROR);
            profileInfo.putBoolean(CharonVpnService.KEY_IS_RETRY, true);
        }
        intent.putExtras(profileInfo);
        ContextCompat.startForegroundService(context, intent);
    }

    public void reconnect() {
        if (mProfile == null) {
            return;
        }
        resetRetryTimer();
        connect(null, false);
    }

    /**
     * Update state and notify all listeners about the change. By using a Handler
     * this is done from the main UI thread and not the initial reporter thread.
     * Also, in doing the actual state change from the main thread, listeners
     * see all changes and none are skipped.
     *
     * @param change the state update to perform before notifying listeners, returns true if state changed
     */
    private void notifyListeners(final Callable<Boolean> change) {
        mHandler.post(() -> {
            try {
                if (change.call()) {    /* otherwise there is no need to notify the listeners */
                    for (VpnStateListener listener : mListeners) {
                        listener.stateChanged();
                    }
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Called when a connection is started.  Sets the currently active VPN
     * profile, resets IMC and Error state variables, sets the State to
     * CONNECTING, increases the connection ID, and notifies all listeners.
     * <p>
     * May be called from threads other than the main thread.
     *
     * @param profile current profile
     */
    public void startConnection(final VpnProfile profile) {
        notifyListeners(() -> {
            resetRetryTimer();
            VpnStateService.this.mConnectionID++;
            VpnStateService.this.mProfile = profile;
            VpnStateService.this.mState = State.CONNECTING;
            VpnStateService.this.mError = ErrorState.NO_ERROR;
            VpnStateService.this.mImcState = ImcState.UNKNOWN;
            VpnStateService.this.mRemediationInstructions.clear();
            return true;
        });
    }

    /**
     * Update the state and notify all listeners, if changed.
     * <p>
     * May be called from threads other than the main thread.
     *
     * @param state new state
     */
    public void setState(final State state) {
        notifyListeners(() -> {
            if (state == State.CONNECTED) {    /* reset counter in case there is an error later on */
                timeoutProvider.reset();
            }
            if (VpnStateService.this.mState != state) {
                VpnStateService.this.mState = state;
                return true;
            }
            return false;
        });
    }

    /**
     * Set the current error state and notify all listeners, if changed.
     * <p>
     * May be called from threads other than the main thread.
     *
     * @param error error state
     */
    public void setError(final ErrorState error) {
        notifyListeners(() -> {
            if (VpnStateService.this.mError != error) {
                if (VpnStateService.this.mError == ErrorState.NO_ERROR) {
                    setRetryTimer(error);
                }
                else if (error == ErrorState.NO_ERROR) {
                    resetRetryTimer();
                }
                VpnStateService.this.mError = error;
                return true;
            }
            return false;
        });
    }

    /**
     * Set the current IMC state and notify all listeners, if changed.
     * <p>
     * Setting the state to UNKNOWN clears all remediation instructions.
     * <p>
     * May be called from threads other than the main thread.
     *
     * @param error error state
     */
    public void setImcState(final ImcState state) {
        notifyListeners(() -> {
            if (state == ImcState.UNKNOWN) {
                VpnStateService.this.mRemediationInstructions.clear();
            }
            if (VpnStateService.this.mImcState != state) {
                VpnStateService.this.mImcState = state;
                return true;
            }
            return false;
        });
    }

    /**
     * Add the given remediation instruction to the internal list.  Listeners
     * are not notified.
     * <p>
     * Instructions are cleared if the IMC state is set to UNKNOWN.
     * <p>
     * May be called from threads other than the main thread.
     *
     * @param instruction remediation instruction
     */
    public void addRemediationInstruction(final RemediationInstruction instruction) {
        mHandler.post(() -> VpnStateService.this.mRemediationInstructions.add(instruction));
    }

    private void setRetryTimer(ErrorState error) {
        retryTimeout = timeoutProvider.getTimeout(error);
        retryIn = retryTimeout;

        Log.e("setting retry timeout: " + retryTimeout);
        Log.e("setting retry in: " + retryIn);
        if (retryTimeout <= 0) {
            return;
        }
        mHandler.sendMessageAtTime(mHandler.obtainMessage(RETRY_MSG),
            SystemClock.uptimeMillis() + RETRY_INTERVAL);
    }

    private void resetRetryTimer() {
        retryTimeout = 0;
        retryIn = 0;
    }

    private static class RetryHandler extends Handler {

        WeakReference<VpnStateService> service;

        public RetryHandler(VpnStateService service) {
            this.service = new WeakReference<>(service);
        }

        @Override
        public void handleMessage(Message msg) {
            if (service.get().retryTimeout <= 0) {
                return;
            }
            if (service.get().getErrorState() == ErrorState.AUTH_FAILED) {
                service.get().setError(ErrorState.NO_ERROR);
                service.get().setState(State.DISABLED);
                return;
            }
            service.get().retryIn -= RETRY_INTERVAL;
            if (service.get().retryIn > 0) {
                long next = SystemClock.uptimeMillis() + RETRY_INTERVAL;

                for (VpnStateListener listener : service.get().mListeners) {
                    listener.stateChanged();
                }
                sendMessageAtTime(obtainMessage(RETRY_MSG), next);
            }
            else {
                Log.exception(new Exception("Trying to exponentially reconnect"));
                service.get().connect(null, false);
            }
        }
    }

    private static class RetryTimeoutProvider {

        private long mRetry;

        private long getBaseTimeout(ErrorState error) {
            switch (error) {
                case AUTH_FAILED:
                    return 10000;
                case PEER_AUTH_FAILED:
                    return 5000;
                case LOOKUP_FAILED:
                    return 5000;
                case UNREACHABLE:
                    return 5000;
                default:
                    return 10000;
            }
        }

        long getTimeout(ErrorState error) {
            long timeout = (long) (getBaseTimeout(error) * Math.pow(2, mRetry++));
            /* return the result rounded to seconds */
            return Math.min((timeout / 1000) * 1000, MAX_RETRY_INTERVAL);
        }

        void reset() {
            mRetry = 0;
        }
    }
}