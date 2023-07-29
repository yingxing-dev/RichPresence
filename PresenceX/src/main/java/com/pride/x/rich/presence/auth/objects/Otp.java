package com.pride.x.rich.presence.auth.objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class Otp {
    private final boolean sms_available;
    private final String ticket;
    private final String phone;

    private Otp(boolean sms_available, @NonNull String ticket, @Nullable String phone) {
        this.sms_available = sms_available;
        this.ticket = ticket;
        this.phone = phone;
    }

    @NonNull
    public String getTicket() {
        return ticket;
    }

    @Nullable
    public String getPhone() {
        return phone;
    }

    public boolean isSmsAvailable() {
        return sms_available;
    }

    @Nullable
    public static Otp create(boolean sms_available, @Nullable String ticket, @Nullable String phone) {
        return ticket == null ? null : new Otp(sms_available, ticket, phone);
    }
}
