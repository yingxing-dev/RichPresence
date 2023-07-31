package com.pride.x.rich.presence.account;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

public class DiscordUser implements Serializable {
    private static final String[] PREFS_DATA = {"discordX", "user"};

    private static final long MAX_EXPIRE = 7L * 24L * 60L * 60L * 1000L;

    @SerializedName("token")
    private final String token;
    @SerializedName("taken_by")
    private final long takenBy;

    public DiscordUser(@NonNull String token) {
        this.takenBy = System.currentTimeMillis();
        this.token = token;
    }

    public String getToken() {
        return token;
    }

    public boolean isExpired() {
        return false;
        // return System.currentTimeMillis() >= takenBy + MAX_EXPIRE;
    }

    public static void save(@NonNull Context context, @NonNull DiscordUser user) {
        getPrefs(context)
                .edit()
                .putString(PREFS_DATA[1], new Gson().toJson(user))
                .apply();
    }

    @SuppressLint("ApplySharedPref")
    public static void clear(@NonNull Context context) {
        getPrefs(context)
                .edit()
                .remove(PREFS_DATA[1])
                .commit();
    }

    public static DiscordUser read(@NonNull Context context) {
        String data = getPrefs(context).getString(PREFS_DATA[1], null);
        if (data == null) return null;

        DiscordUser user = null;
        try {
            user = new Gson().fromJson(data, DiscordUser.class);
        }
        catch (JsonSyntaxException e) {
            e.printStackTrace();
        }

        return user;
    }

    private static SharedPreferences getPrefs(@NonNull Context context) {
        return context.getSharedPreferences(PREFS_DATA[0], Context.MODE_PRIVATE);
    }

}
