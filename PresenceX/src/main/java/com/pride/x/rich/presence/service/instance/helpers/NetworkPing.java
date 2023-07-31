package com.pride.x.rich.presence.service.instance.helpers;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.pride.x.rich.presence.service.PresenceService;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class NetworkPing {

    private static final long PING_PERIOD = 1000;

    private static void tryConnect(
            @Nullable NetworkConnectionListener listener,
            final @NonNull Runnable runnable
    ) {
        // create connection client
        OkHttpClient client = new OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .build();

        // create request
        Request request = new Request.Builder()
                .url("https://google.com")
                .get()
                .build();

        // connect
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (listener != null) listener.onError(runnable);
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (listener != null) listener.onSuccess();
                });
            }
        });
    }

    public static void waitForConnection(
            @Nullable NetworkPingListener listener
    ) {
        // create handler
        final Handler handler = new Handler(Looper.getMainLooper());

        // create listener
        NetworkConnectionListener connectionListener = new NetworkConnectionListener() {
            @Override
            public void onSuccess() {
                if (listener != null)
                    listener.onConnected();
            }

            @Override
            public void onError(@NonNull Runnable r) {
                // retrying
                handler.postDelayed(r, PING_PERIOD);
            }
        };

        // create runnable instance
        final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                Log.e(PresenceService.TAG, "Ping attempt...");
                tryConnect(connectionListener, this);
            }
        };

        // ping
        handler.postDelayed(runnable, PING_PERIOD);
        Log.e(PresenceService.TAG, "Network ping helper started");
    }

    private interface NetworkConnectionListener {
        void onSuccess();
        void onError(@NonNull Runnable runnable);
    }

    public interface NetworkPingListener {
        void onConnected();
    }

}
