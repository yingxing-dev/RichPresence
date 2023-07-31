package com.pride.x.rich.presence.service;

import android.os.Binder;

import androidx.annotation.NonNull;

import com.pride.x.rich.presence.Presence;
import com.pride.x.rich.presence.service.instance.PresenceInstance;

import java.util.ArrayList;
import java.util.List;

public class PresenceBinder extends Binder {

    private final List<PresenceService.PresenceCallback> callbacks = new ArrayList<>();
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
        if (!callbacks.isEmpty()) {
            for (PresenceService.PresenceCallback callback : callbacks) {
                if (callback != null) callback.onReady(instance, user);
            }
        }
    }

    public void disconnect() {
        // clear data
        this.instance = null;
        this.user = null;

        // set connection state
        this.connected = false;

        // callback data
        if (!callbacks.isEmpty()) {
            for (PresenceService.PresenceCallback callback : callbacks) {
                if (callback != null) callback.onDisconnected();
            }
        }
    }

    public void addCallback(PresenceService.PresenceCallback callback) {
        if (connected) callback.onReady(instance, user);
        callbacks.add(callback);
    }
}
