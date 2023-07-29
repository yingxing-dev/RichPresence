package com.pride.x.rich.presence.auth.objects;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

public class OtpResponse implements Serializable {
    @SerializedName("token")
    public String token;
    @SerializedName("code")
    public int code;

    @SerializedName("retry_after")
    public float retry_after;

    public boolean isError() {
        return token == null && code > 0;
    }

}
