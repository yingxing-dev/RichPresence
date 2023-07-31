package com.pride.x.rich.presence.service;

import android.os.Binder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.pride.x.rich.presence.Presence;
import com.pride.x.rich.presence.service.instance.PresenceInstance;

public class PresenceBinder extends Binder {

    private PresenceService.PresenceCallback callback = null;
    private boolean connected = false;

    private PresenceInstance instance = null;
    private Presence.User user;

    public void setInstance(
            @NonNull PresenceInstance instance,
            @NonNull Presence.User user
    ) {
        // save instances
        this.instance = instance;
        this.user = user;

        // set connection state
        connected = true;

        // callback data
        if (callback != null) callback.onReady(instance, user);
    }

    public void disconnect() {
        // clear data
        this.instance = null;
        this.user = null;

        // set connection state
        this.connected = false;

        // callback data
        if (callback != null) callback.onDisconnected();
    }

    public void setCallback(@Nullable PresenceService.PresenceCallback callback) {
        if (callback == null) return;
        if (connected) callback.onReady(instance, user);
        this.callback = callback;
    }
}
