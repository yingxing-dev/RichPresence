package com.pride.x.rich.presence.service.instance;

import android.util.ArrayMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.pride.x.rich.presence.Presence;
import com.pride.x.rich.presence.service.instance.helpers.PresenceImages;
import com.pride.x.rich.presence.service.instance.helpers.PresenceLogger;
import com.pride.x.rich.presence.service.instance.helpers.PresenceRequests;
import com.pride.x.rich.presence.service.instance.helpers.PresenceSocket;

import java.io.IOException;
import java.util.HashMap;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class PresenceInstance {

    // helpers instances
    private final PresenceRequests requests;
    private final PresenceSocket socket;

    // logger
    private final PresenceLogger presenceLogger = new PresenceLogger();

    // user activity values
    private Presence.PresenceInfo presenceInfo = null;
    private String status = Presence.UserStatus.ONLINE;

    // user values
    private final String applicationId;
    private final String token;

    // instance values
    private boolean instanceFinished = false;

    public PresenceInstance(
            @Nullable PresenceInstanceListener listener,
            @NonNull String applicationId,
            @NonNull String token
    ) {
        // set application id
        this.applicationId = applicationId;
        // set token
        this.token = token;
        // create requests helper
        requests = new PresenceRequests(token, applicationId);
        // create socket helper
        socket = new PresenceSocket(new PresenceSocket.PresenceSocketListener() {
            @Override
            public void onReady(@NonNull PresenceSocket socket, @NonNull Presence.User user) {
                if (listener != null) listener.onInstanceReady(user);
                if (presenceInfo != null) update();
            }

            @Override
            public void onFailure(@Nullable String message) {
                if (listener != null) listener.onInstanceFinished(true);
                // mark instance is finished
                instanceFinished = true;
            }

            @Override
            public void onDisconnecting() {
                clearPresence();
            }

            @Override
            public void onClosed(int code, boolean close_internal) {
                if (listener != null) listener.onInstanceFinished(!close_internal);
                // mark instance is finished
                instanceFinished = true;
            }
        }, token, getPresenceLogger());
    }
    public void clearPresence() {
        presenceInfo = null;
        update();
    }

    public boolean isInstanceFinished() {
        return instanceFinished;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public PresenceLogger getPresenceLogger() {
        return presenceLogger;
    }

    public String getToken() {
        return token;
    }

    public void disconnect() {
        socket.disconnect();
    }

    public void setPresence(@NonNull Presence.PresenceInfo info, boolean clearOld) {
        PresenceImages.process(
                clearOld ? info : Presence.PresenceInfo.update(presenceInfo, info),
                requests,
                (presenceInfo) -> {
                    PresenceInstance.this.presenceInfo = presenceInfo;
                    update();
                },
                getPresenceLogger()
        );
    }

    private void update() {
        ArrayMap<String, Object> presence = new ArrayMap<>();

        if (presenceInfo == null) presence.put("activities", new Object[]{});
        else {
            ArrayMap<String, Object> activity = new ArrayMap<>();

            if (presenceInfo.name != null) activity.put("name", presenceInfo.name);
            if (presenceInfo.state != null) activity.put("state", presenceInfo.state);
            if (presenceInfo.details != null) activity.put("details", presenceInfo.details);

            if (presenceInfo.presenceType == Presence.PresenceType.GAME) activity.put("type", 0);
            if (presenceInfo.presenceType == Presence.PresenceType.STREAMING) activity.put("type", 1);
            if (presenceInfo.presenceType == Presence.PresenceType.LISTEN) activity.put("type", 2);
            if (presenceInfo.presenceType == Presence.PresenceType.WATCH) activity.put("type", 3);
            if (presenceInfo.presenceType == Presence.PresenceType.STATUS) activity.put("type", 4);
            if (presenceInfo.presenceType == Presence.PresenceType.COMPETES) activity.put("type", 5);

            ArrayMap<String, Object> assets = new ArrayMap<>();
            if (presenceInfo.largeImage != null)
                assets.put("large_image", presenceInfo.largeImage);
            if (presenceInfo.largeText != null)
                assets.put("large_text", presenceInfo.largeText);
            if (presenceInfo.smallImage != null)
                assets.put("small_image", presenceInfo.smallImage);
            if (presenceInfo.smallText != null)
                assets.put("small_text", presenceInfo.smallText);

            activity.put("assets", assets);
            activity.put("application_id", requests.getApplicationId());

            if (presenceInfo.button1 != null || presenceInfo.button2 != null) {
                String[] buttons = new String[
                        presenceInfo.button1 != null && presenceInfo.button2 != null ? 2 : 1];
                String[] button_urls = new String[
                        presenceInfo.button1 != null && presenceInfo.button2 != null ? 2 : 1];
                if (presenceInfo.button1 != null) {
                    buttons[0] = presenceInfo.button1.label;
                    button_urls[0] = presenceInfo.button1.url;
                }
                if (presenceInfo.button2 != null) {
                    buttons[buttons[0] != null ? 1 : 0] = presenceInfo.button2.label;
                    button_urls[button_urls[0] != null ? 1 : 0] = presenceInfo.button2.url;
                }
                HashMap<String, Object> metadata = new HashMap<>();
                metadata.put("button_urls", button_urls);
                activity.put("buttons", buttons);
                activity.put("metadata", metadata);
            }

            ArrayMap<String, Object> timestamps = new ArrayMap<>();
            if (presenceInfo.startTimeStamp >= 0) timestamps.put("start", presenceInfo.startTimeStamp);
            if (presenceInfo.endTimeStamp > 0) timestamps.put("end", presenceInfo.endTimeStamp);

            activity.put("timestamps", timestamps);
            presence.put("activities", new Object[]{activity});
        }

        presence.put("afk", true);
        presence.put("status", "online");
        presence.put("since", System.currentTimeMillis());

        ArrayMap<String, Object> arr = new ArrayMap<>();
        arr.put("op", 3);
        arr.put("d", presence);

        if (presenceInfo != null && presenceInfo.status != null)
            setStatus(presenceInfo.status);

        String data = socket.getGSON().toJson(arr);
        socket.send(data);
        getPresenceLogger().i("PRESENCE UPDATE: " + data);
    }

    public void setStatus(@NonNull String status) {
        this.status = status;
        updateStatus();
    }

    protected void updateStatus() {
        // create body data
        ArrayMap<String, String> arrayMap = new ArrayMap<>();
        arrayMap.put("settings", status);

        // create request body
        RequestBody body = RequestBody.create(
                requests.getGSON().toJson(arrayMap),
                MediaType.parse("application/json")
        );

        // create request
        Request request = new Request.Builder()
                .url("https://discord.com/api/v9/users/@me/settings-proto/1")
                .addHeader("Authorization", requests.getToken())
                .patch(body)
                .build();

        // connect
        requests.getRequestClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                getPresenceLogger().e("Status change error: " + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                getPresenceLogger().i(
                        "Status change response: " +
                                (response.body() != null ? response.body().string() : null)
                );
            }
        });
    }

    public interface PresenceInstanceListener {
        void onInstanceReady(@NonNull Presence.User user);
        void onInstanceFinished(boolean error);
    }
}
