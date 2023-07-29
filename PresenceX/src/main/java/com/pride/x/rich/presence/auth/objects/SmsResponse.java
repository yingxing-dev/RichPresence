package com.pride.x.rich.presence.auth.objects;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

public class SmsResponse implements Serializable {
    @SerializedName("phone")
    public String phone;
    @SerializedName("60006")
    public int code;

    @SerializedName("retry_after")
    public float retry_after;

    public boolean isError() {
        return phone == null && code > 0;
    }
}
