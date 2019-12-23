package org.strongswan.android.logic;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import androidx.annotation.Keep;

@Keep
public class SimpleFetcher {

    private static ExecutorService mExecutor = Executors.newCachedThreadPool();
    private static final Object LOCK = new Object();
    private static ArrayList<Future> mFutures = new ArrayList<>();
    private static boolean mDisabled;

    public static byte[] fetch(String uri, byte[] data, String contentType) {
        Future<byte[]> future;

        synchronized (LOCK) {
            if (mDisabled) {
                return null;
            }
            future = mExecutor.submit(() -> {
                URL url = new URL(uri);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                try {
                    if (contentType != null) {
                        conn.setRequestProperty("Content-Type", contentType);
                    }
                    if (data != null) {
                        conn.setDoOutput(true);
                        conn.setFixedLengthStreamingMode(data.length);
                        OutputStream out = new BufferedOutputStream(conn.getOutputStream());
                        out.write(data);
                        out.close();
                    }
                    return streamToArray(conn.getInputStream());
                }
                catch (SocketTimeoutException e) {
                    return null;
                }
                finally {
                    conn.disconnect();
                }
            });

            mFutures.add(future);
        }

        try {
            /* this enforces a timeout as the ones set on HttpURLConnection might not work reliably */
            return future.get(10000, TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException | ExecutionException | TimeoutException | CancellationException e) {
            return null;
        }
        finally {
            synchronized (LOCK) {
                mFutures.remove(future);
            }
        }
    }

    /**
     * Enable fetching after it has been disabled.
     */
    public static void enable() {
        synchronized (LOCK) {
            mDisabled = false;
        }
    }

    /**
     * Disable the fetcher and abort any future requests.
     * <p>
     * The native thread is not cancelable as it is working on an IKE_SA (cancelling the methods of
     * HttpURLConnection is not reliably possible anyway), so to abort while fetching we cancel the
     * Future (causing a return from fetch() immediately) and let the executor thread continue its
     * thing in the background.
     * <p>
     * Also prevents future fetches until enabled again (e.g. if we aborted OCSP but would then
     * block in the subsequent fetch for a CRL).
     */
    public static void disable() {
        synchronized (LOCK) {
            mDisabled = true;
            for (Future future : mFutures) {
                future.cancel(true);
            }
        }
    }

    private static byte[] streamToArray(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int len;

        try {
            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
            return out.toByteArray();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        finally {
            in.close();
        }
        return null;
    }
}