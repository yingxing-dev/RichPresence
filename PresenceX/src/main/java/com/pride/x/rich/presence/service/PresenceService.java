package com.pride.x.rich.presence.service;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.pride.x.rich.presence.Presence;
import com.pride.x.rich.presence.service.instance.PresenceInstance;
import com.pride.x.rich.presence.service.instance.helpers.NetworkPing;

@SuppressWarnings("unused")
public class PresenceService extends Service implements PresenceInstance.PresenceInstanceListener {

    public static final String TAG = "PresenceX";

    private PresenceBinder presenceBinder = null;
    private PresenceInstance instance = null;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return (presenceBinder = new PresenceBinder());
    }

    public static void connect(
            @NonNull Context context,
            @NonNull String applicationId,
            @NonNull String token,
            @NonNull PresenceCallback callback
    ) {
        Intent intent = new Intent(context, Presence.class);
        intent.putExtra("applicationId", applicationId);
        intent.putExtra("token", token);
        context.startService(intent);

        // connect to service
        listen(context, callback);
    }

    protected static void listen(@NonNull Context context, @NonNull PresenceCallback callback) {
        context.bindService(
                new Intent(context, Presence.class),
                new ServiceConnection() {
                    @Override
                    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                        if (iBinder instanceof PresenceBinder) {
                            PresenceBinder binder = (PresenceBinder) iBinder;
                            binder.addCallback(callback);
                        }
                        else callback.onDisconnected();
                    }

                    @Override
                    public void onServiceDisconnected(ComponentName componentName) {
                        callback.onDisconnected();
                    }
                }, BIND_AUTO_CREATE
        );
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        // get application id
        String applicationId = intent.getStringExtra("applicationId");
        // get token
        String token = intent.getStringExtra("token");
        // check token and application id on non-null
        if (applicationId != null && token != null) {
            if (instance != null) {
                if (!instance.isInstanceFinished())
                    instance.disconnect();
                instance = null;
            }
            instance = new PresenceInstance(
                    PresenceService.this,
                    applicationId,
                    token
            );
            return START_STICKY;
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onInstanceReady(@NonNull Presence.User user) {
        if (presenceBinder != null) presenceBinder.setInstance(instance, user);
    }

    @Override
    public void onInstanceFinished(boolean error) {
        if (!error) instance = null;
        else {
            final String applicationId = instance.getApplicationId();
            final String token = instance.getToken();
            NetworkPing.waitForConnection(() -> {
                // create presence instance
                instance = new PresenceInstance(
                        PresenceService.this,
                        applicationId,
                        token
                );
            });
        }
        Log.e(TAG, "Presence instance crashed [error=" + error + "]");
        // send disconnect signal
        if (presenceBinder != null)
            presenceBinder.disconnect();
    }



    public interface PresenceCallback {
        void onReady(@NonNull PresenceInstance instance, @NonNull Presence.User user);
        void onDisconnected();
    }
}
