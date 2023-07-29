package com.pride.x.rich.presence.auth.cookies;

import android.content.Context;

import androidx.annotation.NonNull;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;

import okhttp3.JavaNetCookieJar;

public class DiscordCookies {

    public static JavaNetCookieJar get(@NonNull Context context) {
        CookieHandler cookieHandler = new CookieManager(
                new PersistentCookieStore(context), CookiePolicy.ACCEPT_ALL);
        return new JavaNetCookieJar(cookieHandler);
    }

    public static PersistentCookieStore getStore(@NonNull Context context) {
        return new PersistentCookieStore(context);
    }

    public static void clearCookies(@NonNull Context context) {
        new PersistentCookieStore(context).removeAll();
    }
}
