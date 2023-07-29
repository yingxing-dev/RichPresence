package com.pride.x.rich.presence.auth.objects;

import androidx.annotation.NonNull;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

public class SmsData implements Serializable {

    @SerializedName("ticket")
    private final String ticket;

    private SmsData(@NonNull String ticket) {
        this.ticket = ticket;
    }

    public static SmsData create(@NonNull String ticket) {
        return new SmsData(ticket);
    }

}
