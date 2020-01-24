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
package com.protonvpn.android.api;

import android.content.pm.ActivityInfo;

import com.datatheorem.android.trustkit.TrustKit;
import com.protonvpn.android.BuildConfig;
import com.protonvpn.android.bus.EventBus;
import com.protonvpn.android.bus.ForcedLogout;
import com.protonvpn.android.components.AuthorizeException;
import com.protonvpn.android.components.BaseActivity;
import com.protonvpn.android.components.LoaderUI;
import com.protonvpn.android.debug.DebugInfo;
import com.protonvpn.android.models.login.ErrorBody;
import com.protonvpn.android.models.login.GenericResponse;
import com.protonvpn.android.models.login.LoginBody;
import com.protonvpn.android.models.login.LoginInfoBody;
import com.protonvpn.android.models.login.LoginInfoResponse;
import com.protonvpn.android.models.login.LoginResponse;
import com.protonvpn.android.models.login.RefreshBody;
import com.protonvpn.android.models.login.SessionListResponse;
import com.protonvpn.android.models.login.VpnInfoResponse;
import com.protonvpn.android.models.vpn.OpenVPNConfigResponse;
import com.protonvpn.android.models.vpn.ServerList;
import com.protonvpn.android.models.vpn.UserLocation;
import com.protonvpn.android.utils.Json;
import com.protonvpn.android.utils.Log;
import com.protonvpn.android.utils.Storage;
import com.protonvpn.android.utils.User;

import org.jetbrains.annotations.NotNull;
import org.joda.time.DateTime;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

public class ProtonApiRetroFit {

    private static final String VPN_API_URL = "https://" + BuildConfig.API_DOMAIN + "/";
    public static final String SIGNUP_URL = "https://account.protonvpn.com/signup?from=mobile";
    private ProtonVPNRetrofit vpnAPI;
    private OkHttpClient okClient;

    public ProtonApiRetroFit() {
        JacksonConverterFactory converterFactory = JacksonConverterFactory.create(Json.MAPPER);

        OkHttpClient.Builder httpClient = new OkHttpClient.Builder();
        httpClient.connectTimeout(10, TimeUnit.SECONDS);
        httpClient.writeTimeout(10, TimeUnit.SECONDS);
        httpClient.readTimeout(10, TimeUnit.SECONDS);
        if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);
            httpClient.addInterceptor(logging);
        }
        httpClient.addInterceptor(chain -> {
            Request original = chain.request();

            return chain.proceed(prepareHeaders(original).build());
        }).addInterceptor(this::interceptErrors).build();
        httpClient.addInterceptor(new RequestsInterceptor());
        httpClient.addInterceptor(new UserAgentInterceptor());

        OkHttpClient client = BuildConfig.DEBUG ? httpClient.build() :
            httpClient.sslSocketFactory(TrustKit.getInstance().getSSLSocketFactory(BuildConfig.API_DOMAIN),
                TrustKit.getInstance().getTrustManager(BuildConfig.API_DOMAIN)).build();

        vpnAPI = new Retrofit.Builder().baseUrl(VPN_API_URL)
            .client(client)
            .addConverterFactory(converterFactory)
            .build()
            .create(ProtonVPNRetrofit.class);
        okClient = client;

        getOpenVPNConfig(User::setOpenVPNConfig);
    }

    private Response interceptErrors(Interceptor.Chain chain) throws IOException {
        Request request = chain.request();
        okhttp3.Response response = chain.proceed(request);
        ErrorBody errorBody = null;
        try {
            ResponseBody responseBodyCopy = response.peekBody(Long.MAX_VALUE);
            String copy = responseBodyCopy.string();
            if (request.url().toString().contains("https://api.protonvpn.ch/vpn/logicals")) {
                Storage.save(new DebugInfo(new DateTime(), copy));
            }
            errorBody = Json.MAPPER.readValue(copy, ErrorBody.class);
        }
        catch (IOException ignored) {
        }
        if (response.code() == 401) {
            return makeTokenRefreshCall(request, chain);
        }
        if (errorBody != null) {
            // TODO Move forced upgrade to new event and show some kind of message to user after log out
            if (errorBody.getCode() == 10013 || errorBody.getCode() == 5003) {
                EventBus.postOnMain(ForcedLogout.INSTANCE);
            }
            Log.d(errorBody.getError());
            throw new AuthorizeException(errorBody);
        }
        return response;
    }

    private Request.Builder prepareHeaders(Request original) {
        Request.Builder request = original.newBuilder()
            .header("x-pm-appversion", "AndroidVPN_" + BuildConfig.VERSION_NAME)
            .header("x-pm-apiversion", "3")
            .header("x-pm-locale", Locale.getDefault().getLanguage())
            .header("Accept", "application/vnd.protonmail.v1+json");

        if (!User.getAccessToken().isEmpty() && !User.getUuid().isEmpty()) {
            request.addHeader("x-pm-uid", User.getUuid());
            request.addHeader("Authorization", User.getAccessToken());
        }
        request.method(original.method(), original.body());
        return request;
    }

    private Response makeTokenRefreshCall(Request req, Interceptor.Chain chain) throws IOException {
        Call<LoginResponse> call = vpnAPI.postRefresh(new RefreshBody());
        Storage.delete(LoginBody.class);
        LoginResponse body = call.execute().body();
        Storage.save(body);

        Request newRequest;
        newRequest = req.newBuilder()
            .header("Authorization", User.getAccessToken())
            .header("x-pm-uid", User.getUuid())
            .build();

        return chain.proceed(newRequest);
    }

    private static boolean hasNoErrors(retrofit2.Response result, LoaderUI errorContainer) {

        if (result != null && result.raw().code() != 200) {
            if (errorContainer != null) {
                errorContainer.switchToRetry(ErrorBody.buildError(
                    "Response: " + result.raw().code() + " result : " + result.raw().body()));
                return false;
            }
        }
        else {
            if (result == null || result.raw() == null) {
                if (errorContainer != null) {
                    errorContainer.switchToRetry(ErrorBody.buildError("Got null body"));
                }
                return false;
            }
        }
        return true;

    }

    private void fetchLoginInfo(final BaseActivity activity, final String email,
                                final NetworkResultCallback<LoginInfoResponse> callback) {
        BackoffCallback<LoginInfoResponse> backoffCallback = makeCallback(activity, callback);
        backoffCallback.onStart();
        vpnAPI.postLoginInfo(new LoginInfoBody(email)).enqueue(backoffCallback);
    }

    private static <T> BackoffCallback<T> makeCallback(final NetworkLoader loader,
                                                       final NetworkResultCallback<T> callback) {
        return new BackoffCallback<T>() {
            @Override
            public void onStart() {
                disableRotations(loader);
            }

            @Override
            public void onResponse(@NonNull Call<T> call, @NonNull retrofit2.Response<T> response) {
                if (hasNoErrors(response, loader.getNetworkFrameLayout())) {
                    loader.getNetworkFrameLayout().switchToEmpty();
                    callback.onSuccess(response.body());
                }
                else {
                    callback.onFailure();
                }
                enableRotations(loader);
            }

            @Override
            public void onFailedAfterRetry(Throwable t) {
                if (BuildConfig.DEBUG) {
                    t.printStackTrace();
                }
                loader.getNetworkFrameLayout()
                    .switchToRetry(t instanceof AuthorizeException ? ((AuthorizeException) t).getErrorBody() :
                        ErrorBody.buildError(t.getLocalizedMessage()));
                enableRotations(loader);
                callback.onFailure();
            }
        };
    }

    private static <T> BackoffCallback<T> makeCallback(final NetworkResultCallback<T> callback) {
        return new BackoffCallback<T>() {
            @Override
            public void onStart() {
            }

            @Override
            public void onResponse(@NonNull Call<T> call, @NonNull retrofit2.Response<T> response) {
                callback.onSuccess(response.body());
            }

            @Override
            public void onFailedAfterRetry(Throwable t) {
                t.printStackTrace();
                callback.onSuccess(null);
            }
        };
    }

    private static void enableRotations(NetworkLoader loader) {
        BaseActivity activity = (BaseActivity) loader.getContext();
        if (activity != null) {
            activity.checkOrientation();
        }
    }

    private static void disableRotations(NetworkLoader loader) {
        BaseActivity activity = (BaseActivity) loader.getContext();
        if (activity != null) {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        }
    }

    public void getVPNInfo(@NonNull final BaseActivity activity,
                           @NonNull final NetworkResultCallback<VpnInfoResponse> callback) {
        vpnAPI.getVPNInfo().enqueue(makeCallback(activity, callback));

        activity.getLoadingContainer()
            .setOnRequestRetryListener(() -> activity.getLoadingContainer().switchToEmptyView());
    }

    public void logout(@NonNull NetworkResultCallback<GenericResponse> callback) {
        vpnAPI.postLogout().enqueue(makeCallback(callback));
    }

    @Nullable
    public Call<SessionListResponse> getSession(
        @NonNull NetworkResultCallback<SessionListResponse> callback) {
        Call<SessionListResponse> call = vpnAPI.getSession();
        call.enqueue(new BackoffCallback<SessionListResponse>() {
            @Override
            public void onStart() {
            }

            @Override
            public void onFailedAfterRetry(@NotNull Throwable t) {
                Log.exception(new Throwable("Failed sessions: " + t.getLocalizedMessage()));
                callback.onSuccess(new SessionListResponse(0, new ArrayList<>()));
            }

            @Override
            public void onResponse(@NonNull Call<SessionListResponse> call,
                                   @NonNull retrofit2.Response<SessionListResponse> response) {
                if (response.body() == null) {
                    Log.exception(new Throwable("Null response on sessions: " + response.toString()));
                }
                callback.onSuccess(response.body() == null ? new SessionListResponse(0, new ArrayList<>()) :
                    response.body());
            }
        });
        return call;
    }

    public void getLocation(@NonNull NetworkResultCallback<UserLocation> callback) {
        vpnAPI.getLocation().enqueue(makeCallback(callback));
    }

    public void getOpenVPNConfig(@NonNull NetworkResultCallback<OpenVPNConfigResponse> callback) {

        vpnAPI.getOpenVPNConfig().enqueue(makeCallback(callback));
    }

    public void postBugReport(@NonNull final BaseActivity activity, @NonNull RequestBody params,
                              @NonNull final NetworkResultCallback<GenericResponse> callback) {

        vpnAPI.postBugReport(params).enqueue(makeCallback(activity, callback));
        activity.getLoadingContainer().switchToLoading();
        activity.getLoadingContainer()
            .setOnRequestRetryListener(() -> activity.getLoadingContainer().switchToEmptyView());
    }

    public void postLogin(@NonNull final BaseActivity activity, @NonNull LoginBody body,
                          @NonNull final NetworkResultCallback<LoginResponse> callback) {

        vpnAPI.postLogin(body).enqueue(makeCallback(activity, callback));

        activity.getLoadingContainer()
            .setOnRequestRetryListener(() -> activity.getLoadingContainer().switchToEmptyView());
    }

    public void getServerList(@Nullable final NetworkLoader activity, @Nullable final String ip,
                              @NonNull final NetworkResultCallback<ServerList> callback) {
        BackoffCallback<ServerList> backoffCallback;
        if (activity != null) {
            final LoaderUI loader = activity.getNetworkFrameLayout();
            loader.switchToLoading();
            backoffCallback = makeCallback(activity, callback);
        }
        else {
            backoffCallback = makeCallback(callback);
        }
        backoffCallback.onStart();
        vpnAPI.getServers(ip).enqueue(backoffCallback);
    }

    public void postLoginInfo(@NonNull final BaseActivity activity, @NonNull final String email,
                              @NonNull final NetworkResultCallback<LoginInfoResponse> callback) {

        Storage.delete(LoginResponse.class);
        activity.getLoadingContainer().switchToLoading();
        fetchLoginInfo(activity, email, callback);
        activity.getLoadingContainer().setOnRequestRetryListener(() -> {
            activity.getLoadingContainer().switchToLoading();
            fetchLoginInfo(activity, email, callback);
        });
    }

    public @NonNull
    OkHttpClient getOkClient() {
        return okClient;
    }
}
