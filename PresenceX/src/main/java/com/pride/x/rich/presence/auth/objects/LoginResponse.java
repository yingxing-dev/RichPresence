package com.pride.x.rich.presence.auth.objects;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

public class LoginResponse implements Serializable {
    @SerializedName("code")
    public int code;
    @SerializedName("token")
    public String token;
    @SerializedName("mfa")
    public boolean mfa;
    @SerializedName("sms")
    public boolean sms;
    @SerializedName("ticket")
    public String ticket;

    @SerializedName("captcha_key")
    public String[] captcha_key;

    @SerializedName("captcha_service")
    public String captcha_service;

    @SerializedName("captcha_sitekey")
    public String captcha_sitekey;

    @SerializedName("retry_after")
    public float retry_after;

    public boolean isError() {
        return ticket == null && token == null && code > 0;
    }
}
