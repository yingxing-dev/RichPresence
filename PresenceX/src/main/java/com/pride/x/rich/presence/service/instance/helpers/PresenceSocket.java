package com.pride.x.rich.presence.service.instance.helpers;

import android.util.ArrayMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.pride.x.rich.presence.Presence;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.internal.ws.RealWebSocket;

public class PresenceSocket {
    // Gson
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create();

    // instances & listeners
    private final PresenceSocketListener listener;
    private final RealWebSocket socket;

    // Logger
    private final PresenceLogger logger;

    // user values
    private final String token;

    // heartbeat values
    private Thread heartbeatThread = null;
    private int heartbeat_interval = 0;
    private int seq = 0;

    // socket values
    private boolean socketClosed = true;

    public PresenceSocket(
            @Nullable PresenceSocketListener listener,
            @NonNull String token,
            @NonNull PresenceLogger logger
    ) {
        // set logger
        this.logger = logger;

        // set token
        this.token = token;

        // set listener
        this.listener = listener;

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
                logger.e("Closed: [code=" + code + ", reason=" + reason + "]");

                // stop heartbeat polling
                stopPollHeartbeat();

                // set closed flag
                socketClosed = true;

                // if closed on gateway error
                if (listener != null)
                    listener.onClosed(code, code == 4000);
            }

            @Override
            public void onClosing(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
                super.onClosing(webSocket, code, reason);
                logger.i("Closing: [code=" + code + ", reason=" + reason + "]");
            }

            @Override
            public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, @Nullable Response response) {
                super.onFailure(webSocket, t, response);

                // stop heartbeat polling
                stopPollHeartbeat();

                // set closed flag
                socketClosed = true;

                if (listener != null) listener.onFailure(t.getMessage());
                logger.e("Failure: " + t.getMessage());
            }

            @Override
            public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
                super.onMessage(webSocket, text);
                logger.i("Message: " + text);

                // set closed flag
                socketClosed = false;

                // create body container
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
    }

    private void onMessageDispatch(ArrayMap<String, Object> map) {
        String state = (String) Objects.requireNonNull(map.get("t"));
        if ("READY".equals(state)) {
            //noinspection unchecked
            Map<String, Object> data = (Map<String, Object>) ((Map<String, Object>) Objects.requireNonNull(map.get("d"))).get("user");
            String discriminator = data != null ? (data.get("discriminator") == null ?
                    null : (String) data.get("discriminator")) : null;
            String username = (String) (data != null ? data.get("username") : null);

            assert username != null;
            if (listener != null)
                listener.onReady(
                        PresenceSocket.this,
                        new Presence.User(discriminator, username)
                );
        }
    }

    public Gson getGSON() {
        return GSON;
    }

    public void send(@NonNull String data) {
        if (socketClosed) return;
        socket.send(data);
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

        logger.e("Identification: " + GSON.toJson(arrayMap));
        socket.send(GSON.toJson(arrayMap));
    }

    public void disconnect() {
        if (socket != null) {
            if (listener != null)
                listener.onDisconnecting();
            socket.close(4000, null);
        }
    }

    public interface PresenceSocketListener {
        void onReady(@NonNull PresenceSocket socket, @NonNull Presence.User user);
        void onFailure(@Nullable String message);
        void onDisconnecting();
        void onClosed(int code, boolean close_internal);
    }
}
