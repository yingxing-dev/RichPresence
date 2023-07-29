package com.pride.x.rich.presence.service;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.ArrayMap;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.pride.x.rich.presence.Presence;
import com.pride.x.rich.presence.objects.ExternalUrl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.internal.ws.RealWebSocket;

@SuppressWarnings("unused")
public class PresenceService extends Service {

    public static final String TAG = "PresenceX";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

    private OkHttpClient requestClient = null;
    private RealWebSocket socket = null;

    private String token = null;

    private Thread heartbeatThread = null;
    private int heartbeat_interval = 0;
    private int seq = 0;
    private boolean socketClosed = false;

    private String applicationId = null;

    private Presence.PresenceInfo presenceInfo = null;
    private String status = Presence.UserStatus.ONLINE;

    private PresenceBinder presenceBinder = null;
    private int serviceId = -1;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return (presenceBinder = new PresenceBinder((Presence) this));
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

    private void connect(@NonNull String applicationId) {
        // create connection client (for requests)
        requestClient = new OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .build();

        // create connection client (for socket)
        OkHttpClient client = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build();

        // create request for web socket
        Request request = new Request.Builder()
                .url("wss://gateway.discord.gg/?encoding=json&v=10")
                .build();

        // open connection socket
        socket = (RealWebSocket) client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onClosed(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
                super.onClosed(webSocket, code, reason);
                Log.e(TAG, "Closed: [code=" + code + ", reason=" + reason + "]");

                // stop heartbeat polling
                stopPollHeartbeat();

                // set flag
                socketClosed = true;

                // if closed on gateway error
                if (code != 4000) // reconnect
                    connect(applicationId);
            }

            @Override
            public void onClosing(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
                super.onClosing(webSocket, code, reason);
                Log.e(TAG, "Closing: [code=" + code + ", reason=" + reason + "]");
            }

            @Override
            public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, @Nullable Response response) {
                super.onFailure(webSocket, t, response);

                // stop heartbeat polling
                stopPollHeartbeat();

                // set flag
                socketClosed = true;

                // reconnect
                connect(applicationId);
                Log.e(TAG, "Failure: " + t.getMessage());
            }

            @Override
            public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
                super.onMessage(webSocket, text);
                Log.e(TAG, "Message: " + text);

                ArrayMap<String, Object> map = GSON.fromJson(
                        text, new TypeToken<ArrayMap<String, Object>>() {
                        }.getType()
                );

                Object o = map.get("s");
                if (o != null) seq = ((Double) o).intValue();

                int opcode = ((Double) Objects.requireNonNull(map.get("op"))).intValue();
                switch (opcode) {
                    case 0:
                        onMessageDispatch(map);
                        break;
                    case 10:
                        //noinspection unchecked
                        Map<String, Object> data = (Map<String, Object>) map.get("d");
                        assert data != null;
                        heartbeat_interval = ((Double) Objects.requireNonNull(data.get("heartbeat_interval"))).intValue();
                        pollHeartbeat();
                        sendIdentify();
                        break;
                    case 1:
                    case 11:
                        pollHeartbeat();
                        break;
                }
            }
        });

        // save applicationId
        this.applicationId = applicationId;
    }

    private void onMessageDispatch(ArrayMap<String, Object> map) {
        String state = (String) Objects.requireNonNull(map.get("t"));
        if ("READY".equals(state)) {
            //noinspection unchecked
            Map<String, Object> data = (Map<String, Object>) ((Map<String, Object>) Objects.requireNonNull(map.get("d"))).get("user");
            String discriminator = data != null ? (data.get("discriminator") == null ? null : (String) data.get("discriminator")) : null;
            String username = (String) (data != null ? data.get("username") : null);

            assert username != null;
            presenceBinder.setConnected(true, new Presence.User(discriminator, username));

            // send old info
            if (presenceInfo != null) update();
        }
    }

    private void pollHeartbeat() {
        // if poll thread is already worked
        stopPollHeartbeat();

        // create poll thread
        heartbeatThread = new Thread(() -> {
            try {
                if (heartbeat_interval < 10000) throw new RuntimeException("Discord gateway heartbeat invalid");
                Thread.sleep(heartbeat_interval);
                socket.send(("{\"op\":1, \"d\":" + (seq == 0 ? "null" : Integer.toString(seq)) + "}"));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        // start poll
        heartbeatThread.start();
    }

    private void stopPollHeartbeat() {
        if (heartbeatThread == null) return;
        if (!heartbeatThread.isInterrupted())
            heartbeatThread.interrupt();
    }

    private void sendIdentify() {
        ArrayMap<String, Object> properties = new ArrayMap<>();
        properties.put("$os", "Windows");
        properties.put("$browser", "Chrome");
        properties.put("$device", "");

        ArrayMap<String, Object> data = new ArrayMap<>();
        data.put("token", token);
        data.put("properties", properties);
        data.put("compress", false);
        data.put("large_threshold", 250);
        data.put("intents", 0);

        ArrayMap<String, Object> arrayMap = new ArrayMap<>();
        arrayMap.put("op", 2);
        arrayMap.put("d", data);

        Log.e(TAG, "Identification: " + GSON.toJson(arrayMap));
        socket.send(GSON.toJson(arrayMap));
    }

    public void clear() {
        set(null, false);
    }

    public void set(@Nullable Presence.PresenceInfo info, boolean clearOld) {
        updatePresenceImage(
                clearOld || info == null ? info : Presence.PresenceInfo.update(presenceInfo, info),
                (presenceInfo) -> {
                    PresenceService.this.presenceInfo = presenceInfo;
                    update();
                });
    }

    private void updatePresenceImage(
            final @Nullable Presence.PresenceInfo info,
            @NonNull ExternalImagesReadyListener listener
    ) {
        if (info == null) {
            listener.onReady(null);
            return;
        }
        Object[] largeImage = processImageLink(info.largeImage);
        Object[] smallImage = processImageLink(info.smallImage);

        List<String> external_urls = new ArrayList<>();
        boolean externalLarge = false;
        boolean externalSmall = false;
        if (largeImage != null && !(boolean) largeImage[1])
        {
            external_urls.add((String) largeImage[0]);
            externalLarge = true;
        }
        else {
            if (largeImage != null) info.largeImage = (String) largeImage[0];
        }
        if (smallImage != null && !(boolean) smallImage[1])
        {
            external_urls.add((String) smallImage[0]);
            externalSmall = true;
        }
        else {
            if (smallImage != null) info.smallImage = (String) smallImage[0];
        }

        String[] urls = external_urls.toArray(new String[0]);
        if (urls.length > 0) {
            // create request url
            String requestUrl = String.format(
                    Locale.getDefault(),
                    "https://discord.com/api/v9/applications/%s/external-assets",
                    applicationId
            );
            // create body data
            ArrayMap<String, Object> arrayMap = new ArrayMap<>();
            arrayMap.put("urls", urls);
            // create request body
            RequestBody body = RequestBody.create(
                    GSON.toJson(arrayMap),
                    MediaType.parse("application/json")
            );
            // create request
            Request request = new Request.Builder()
                    .url(requestUrl)
                    .addHeader("Authorization", token)
                    .post(body)
                    .build();
            // connect...
            boolean finalExternalLarge = externalLarge;
            boolean finalExternalSmall = externalSmall;
            requestClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    listener.onReady(presenceInfo);
                    Log.e(TAG, "EXTERNAL URLS: " + e.getMessage());
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    ResponseBody responseBody = response.body();
                    if (responseBody != null) {
                        String body = responseBody.string();
                        Log.e(TAG, "EXTERNAL URLS: " + body);
                        try {
                            List<ExternalUrl> externalUrlList = GSON.fromJson(
                                    body,
                                    new TypeToken<List<ExternalUrl>>(){}.getType()
                            );
                            if (externalUrlList == null || externalUrlList.size() == 0)
                                onFailure(call, new IOException("External urls is empty"));
                            else {
                                if (externalUrlList.size() == 1) {
                                    if (finalExternalLarge)
                                        info.largeImage = String.format(
                                                Locale.getDefault(),
                                                "mp:%s",
                                                externalUrlList.get(0).external_asset_path
                                        );
                                    else if (finalExternalSmall)
                                        info.smallImage = String.format(
                                                Locale.getDefault(),
                                                "mp:%s",
                                                externalUrlList.get(0).external_asset_path
                                        );
                                    listener.onReady(info);
                                    return;
                                }
                                info.largeImage = String.format(
                                        Locale.getDefault(),
                                        "mp:%s",
                                        externalUrlList.get(0).external_asset_path
                                );
                                info.smallImage = String.format(
                                        Locale.getDefault(),
                                        "mp:%s",
                                        externalUrlList.get(1).external_asset_path
                                );
                                listener.onReady(info);
                            }
                        }
                        catch (JsonSyntaxException e) {
                            onFailure(call, new IOException("JSON syntax error"));
                        }
                    }
                    else onFailure(call, new IOException("No response body"));
                }
            });
        }
        else listener.onReady(info);
    }


    private Object[] processImageLink(@Nullable String url) {
        String link = url;

        if (link == null) return null;
        if (link.isEmpty()) return null;
        if (link.contains("://")) link = link.split("://")[1];

        if (link.startsWith("media.discordapp.net/"))
            return new Object[] {
                    link.replace("media.discordapp.net/", "mp:"),
                    true
            };
        else if (link.startsWith("cdn.discordapp.com"))
            return new Object[] {
                    link.replace("cdn.discordapp.com/", "../../") + "#",
                    true
            };

        return new Object[] {
                url, false
        };
    }

    private interface ExternalImagesReadyListener {
        void onReady(@Nullable Presence.PresenceInfo info);
    }

    public void update() {
        if (socket == null || socketClosed) return;

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
            activity.put("application_id", applicationId);

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

        socket.send(GSON.toJson(arr));
        Log.e(TAG, "PRESENCE UPDATE: " + GSON.toJson(arr));
    }

    public void setStatus(@NonNull String status) {
        this.status = status;
        updateStatus();
    }

    protected void updateStatus() {
        if (requestClient == null) return;

        // create body data
        ArrayMap<String, String> arrayMap = new ArrayMap<>();
        arrayMap.put("settings", status);

        // create request body
        RequestBody body = RequestBody.create(
                GSON.toJson(arrayMap),
                MediaType.parse("application/json")
        );

        // create request
        Request request = new Request.Builder()
                .url("https://discord.com/api/v9/users/@me/settings-proto/1")
                .addHeader("Authorization", token)
                .patch(body)
                .build();

        // connect
        requestClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Status change error: " + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                Log.e(
                        TAG,
                        "Status change response: " + (response.body() != null ? response.body().string() : null)
                );
            }
        });
    }

    protected void disconnect() {
        if (socket != null) {
            clear();
            socket.close(4000, null);
        }
        if (serviceId != -1) stopSelf(serviceId);
    }

    public static void disconnect(@NonNull Context context) {
        Presence.listen(context, new PresenceCallback() {
            @Override
            public void onSuccess(@NonNull Presence presence, @NonNull Presence.User user) {
                presence.disconnect();
                context.stopService(new Intent(context, PresenceService.class));
            }

            @Override
            public void onDisconnected() {
                // unused
            }
        });
    }



    @Override
    public void onDestroy() {
        disconnect();
        super.onDestroy();
        serviceId = -1;
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
            // check token on non-null
            if (this.token != null) {
                // if socket is opened
                if (this.token.equals(token) && !socketClosed)
                    return START_NOT_STICKY;
            }
            // if service already connected -> close
            if (!socketClosed && socket != null)
                socket.close(4000, "Close");
            // set service instance id
            serviceId = startId;
            // set token
            this.token = token;
            // reset socket close state
            socketClosed = false;
            // connect
            connect(applicationId);
            return START_STICKY;
        }
        return START_NOT_STICKY;
    }

    public interface PresenceCallback {
        void onSuccess(@NonNull Presence presence, @NonNull Presence.User user);
        void onDisconnected();
    }
}
