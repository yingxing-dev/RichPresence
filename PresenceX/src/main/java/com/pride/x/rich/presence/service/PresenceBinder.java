package com.pride.x.rich.presence.service;

import android.os.Binder;

import androidx.annotation.NonNull;

import com.pride.x.rich.presence.Presence;

import java.util.ArrayList;
import java.util.List;

public class PresenceBinder extends Binder {

    private final List<PresenceService.PresenceCallback> callbacks = new ArrayList<>();
    private boolean connected = false;

    private final Presence presence;
    private Presence.User user;

    public PresenceBinder(@NonNull Presence presence) {
        this.presence = presence;
    }

    public Presence getPresence() {
        return presence;
    }

    public void setConnected(boolean connected, @NonNull Presence.User user) {
        // save instances
        this.connected = connected;
        this.user = user;

        // callback data
        if (!callbacks.isEmpty()) {
            for (PresenceService.PresenceCallback callback : callbacks) {
                if (callback != null) callback.onSuccess(getPresence(), user);
            }
        }
    }

    public void addCallback(PresenceService.PresenceCallback callback) {
        if (connected) callback.onSuccess(presence, user);
        callbacks.add(callback);
    }
}
