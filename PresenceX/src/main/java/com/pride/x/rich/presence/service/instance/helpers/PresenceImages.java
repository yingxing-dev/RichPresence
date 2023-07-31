package com.pride.x.rich.presence.service.instance.helpers;

import static com.pride.x.rich.presence.service.PresenceService.TAG;

import android.util.ArrayMap;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.pride.x.rich.presence.Presence;
import com.pride.x.rich.presence.objects.ExternalUrl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class PresenceImages {

    public static void process(
            final @Nullable Presence.PresenceInfo info,
            final @Nullable PresenceRequests requests,
            @NonNull ImagesReadyListener listener
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

        if (requests == null) {
            listener.onReady(info);
            return;
        }

        String[] urls = external_urls.toArray(new String[0]);
        if (urls.length > 0) {
            // create request url
            String requestUrl = String.format(
                    Locale.getDefault(),
                    "https://discord.com/api/v9/applications/%s/external-assets",
                    requests.getApplicationId()
            );
            // create body data
            ArrayMap<String, Object> arrayMap = new ArrayMap<>();
            arrayMap.put("urls", urls);
            // create request body
            RequestBody body = RequestBody.create(
                    requests.getGSON().toJson(arrayMap),
                    MediaType.parse("application/json")
            );
            // create request
            Request request = new Request.Builder()
                    .url(requestUrl)
                    .addHeader("Authorization", requests.getToken())
                    .post(body)
                    .build();
            // connect...
            boolean finalExternalLarge = externalLarge;
            boolean finalExternalSmall = externalSmall;
            requests.getRequestClient().newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    listener.onReady(info);
                    Log.e(TAG, "EXTERNAL URLS: " + e.getMessage());
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    ResponseBody responseBody = response.body();
                    if (responseBody != null) {
                        String body = responseBody.string();
                        Log.e(TAG, "EXTERNAL URLS: " + body);
                        try {
                            List<ExternalUrl> externalUrlList = requests.getGSON().fromJson(
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

    private static Object[] processImageLink(@Nullable String url) {
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

    public interface ImagesReadyListener {
        void onReady(@Nullable Presence.PresenceInfo info);
    }
}
