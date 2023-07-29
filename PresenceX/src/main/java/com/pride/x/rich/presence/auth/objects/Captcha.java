package com.pride.x.rich.presence.auth.objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class Captcha {
    private final String service;
    private final String sitekey;

    private Captcha(@NonNull String service, @NonNull String sitekey) {
        this.service = service;
        this.sitekey = sitekey;
    }

    public String getService() {
        return service;
    }

    public String getSitekey() {
        return sitekey;
    }

    @Nullable
    public static Captcha create(@Nullable String service, @Nullable String sitekey) {
        return service != null && sitekey != null ? new Captcha(service, sitekey) : null;
    }
}
