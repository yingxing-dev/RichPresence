package com.pride.x.rich.presence.auth.objects;

import androidx.annotation.NonNull;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

public class OtpData implements Serializable {
    public static class Code implements Serializable {
        @SerializedName("ticket")
        private final String ticket;
        @SerializedName("code")
        private final String code;

        @SerializedName("gift_code_sku_id")
        private final String gift_code_sku_id = null;
        @SerializedName("login_source")
        private final String login_source = null;

        private Code(@NonNull String ticket, @NonNull String code) {
            this.ticket = ticket;
            this.code = code;
        }

        public static Code create(@NonNull String ticket, @NonNull String code) {
            return new Code(ticket, code);
        }
    }

    public static class Sms implements Serializable {
        @SerializedName("ticket")
        private final String ticket;
        @SerializedName("code")
        private final String code;

        private Sms(@NonNull String ticket, @NonNull String code) {
            this.ticket = ticket;
            this.code = code;
        }

        public static Sms create(@NonNull String ticket, @NonNull String code) {
            return new Sms(ticket, code);
        }
    }
}
