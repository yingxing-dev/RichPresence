package com.pride.x.rich.presence.service.instance.helpers;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class PresenceLogger {
    private static final String LOG_TAG = "PresenceX";
    private static final int ERROR_TYPE = 2;
    private static final int INFO_TYPE = 1;

    private final List<Object[]> firstStack = new ArrayList<>();
    private PresenceLogListener listener = null;

    public void setLogListener(@Nullable PresenceLogListener listener) {
        if (listener == null) return;
        if (!firstStack.isEmpty()) {
            for (Object[] stack : firstStack) {
                switch ((int) stack[0]) {
                    case ERROR_TYPE:
                        listener.onLogError((String) stack[1], (String) stack[2]);
                        break;
                    case INFO_TYPE:
                        listener.onLogInfo((String) stack[1], (String) stack[2]);
                        break;
                }
            }
        }
        this.listener = listener;
    }

    public void e(@NonNull String message) {
        if (listener != null) listener.onLogError(LOG_TAG, message);
        else firstStack.add(new Object[]{ERROR_TYPE, LOG_TAG, message});
        Log.e(LOG_TAG, message);
    }

    public void i(@NonNull String message) {
        if (listener != null) listener.onLogInfo(LOG_TAG, message);
        else firstStack.add(new Object[]{INFO_TYPE, LOG_TAG, message});
        Log.i(LOG_TAG, message);
    }

    public interface PresenceLogListener {
        void onLogError(@NonNull String tag, @NonNull String message);
        void onLogInfo(@NonNull String tag, @NonNull String message);
    }
}
