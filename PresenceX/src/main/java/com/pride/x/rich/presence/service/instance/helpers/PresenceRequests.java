package com.pride.x.rich.presence.service.instance.helpers;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import okhttp3.OkHttpClient;

public class PresenceRequests {
    // Gson
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create();

    // request client instance
    private final OkHttpClient requestClient;

    // cache values
    private final String applicationId;
    private final String token;

    public PresenceRequests(
            @NonNull String token,
            @NonNull String applicationId
    ) {
        // set application id
        this.applicationId = applicationId;
        // set token
        this.token = token;
        // create request client
        requestClient = new OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .build();
    }

    public Gson getGSON() {
        return GSON;
    }

    public OkHttpClient getRequestClient() {
        return requestClient;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public String getToken() {
        return token;
    }
}
