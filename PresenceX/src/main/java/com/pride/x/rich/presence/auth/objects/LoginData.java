package com.pride.x.rich.presence.auth.objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

public class LoginData implements Serializable {

    @SerializedName("captcha_key")
    private final String captcha_key;
    @SerializedName("password")
    private final String password;
    @SerializedName("login")
    private final String login;

    @SerializedName("gift_code_sku_id")
    private final String gift_code_sku_id = null;
    @SerializedName("login_source")
    private final String login_source = null;
    @SerializedName("undelete")
    private final boolean undelete = false;

    private LoginData(
            @NonNull String login,
            @NonNull String password,
            @Nullable String captcha_key
    ) {
        this.captcha_key = captcha_key;
        this.password = password;
        this.login = login;
    }

    public static LoginData create(
            @NonNull String login,
            @NonNull String password
    ) {
        return new LoginData(login, password, null);
    }

    public static LoginData create(
            @NonNull String login,
            @NonNull String password,
            @NonNull String captcha_key
    ) {
        return new LoginData(login, password, captcha_key);
    }

}
