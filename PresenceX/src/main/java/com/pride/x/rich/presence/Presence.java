package com.pride.x.rich.presence;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;
import com.pride.x.rich.presence.service.PresenceService;

import java.io.Serializable;


@SuppressWarnings("unused")
public class Presence extends PresenceService {

    public static class PresenceInfo {
        private static final String DEFAULT_STATUS = UserStatus.ONLINE;
        public PresenceType presenceType;
        public long startTimeStamp = System.currentTimeMillis();
        public long endTimeStamp;
        public String largeImage;
        public String largeText;
        public String smallImage;
        public String smallText;
        public String details;
        public String state;
        public String name;

        public Button button1;
        public Button button2;
        public String status;

        private PresenceInfo(
                @Nullable PresenceType presenceType,
                long startTimeStamp, long endTimeStamp,
                @Nullable String largeText,
                @Nullable String largeImage,
                @Nullable String smallImage,
                @Nullable String smallText,
                @Nullable String details,
                @Nullable String status,
                @Nullable String state,
                @Nullable String name,
                @Nullable Button button1,
                @Nullable Button button2
        ) {
            this.endTimeStamp = endTimeStamp;
            this.presenceType = presenceType;
            this.largeImage = largeImage;
            this.largeText = largeText;
            this.smallImage = smallImage;
            this.smallText = smallText;
            this.details = details;
            this.status = status == null ? DEFAULT_STATUS : status;
            this.state = state;
            this.name = name;
            this.button1 = button1;
            this.button2 = button2;
        }

        public PresenceInfo() {
            this.endTimeStamp = -1;
            this.presenceType = PresenceType.GAME;
            this.largeImage = null;
            this.largeText = null;
            this.smallImage = null;
            this.smallText = null;
            this.details = null;
            this.status = DEFAULT_STATUS;
            this.state = null;
            this.name = null;
            this.button1 = null;
            this.button2 = null;
        }

        @NonNull
        public static PresenceInfo update(
                @Nullable PresenceInfo info1,
                @NonNull PresenceInfo info2
        ) {
            if (info1 == null) return info2;

            PresenceType presenceTypeCode = info1.presenceType;
            long startTimeStamp = info1.startTimeStamp;
            long endTimeStamp = info1.endTimeStamp;
            String largeImage = info1.largeImage;
            String largeText = info1.largeText;
            String smallImage = info1.smallImage;
            String smallText = info1.smallText;
            String details = info1.details;
            String status = info1.status;
            String state = info1.state;
            String name = info1.name;
            Button button1 = info1.button1;
            Button button2 = info1.button2;

            if (info2.presenceType != presenceTypeCode) presenceTypeCode = info2.presenceType;
            if (startTimeStamp != info2.startTimeStamp) startTimeStamp = info2.startTimeStamp;
            if (endTimeStamp != info2.endTimeStamp) startTimeStamp = info2.endTimeStamp;
            if (info2.details == null && details != null) details = null;

            if (button1 == null && info2.button1 != null) button1 = info2.button1;
            else if (button1 != null && info2.button1 != null) {
                if (!button1.equals(info2.button1)) button1 = info2.button1;
            }
            if (button2 == null && info2.button2 != null) button2 = info2.button2;
            else if (button2 != null && info2.button2 != null) {
                if (!button2.equals(info2.button2)) button2 = info2.button2;
            }

            else if (info2.details != null & details != null)
                if (!info2.details.equals(details)) details = info2.details;
            if (info2.state == null && state != null) state = null;
            else if (info2.state != null & state != null)
                if (!info2.state.equals(state)) state = info2.state;
            if (info2.name == null && name != null) name = null;
            else if (info2.name != null && name != null)
                if (!info2.name.equals(name)) name = info2.name;
            if (info2.largeImage == null && largeImage != null) largeImage = null;
            else if (info2.largeImage != null && largeImage != null)
                if (!info2.largeImage.equals(largeImage)) largeImage = info2.largeImage;
            if (info2.smallImage == null && smallImage != null) smallImage = null;
            else if (info2.smallImage != null && smallImage != null)
                if (!info2.smallImage.equals(smallImage)) smallImage = info2.smallImage;
            if (info2.status == null && status != null) status = null;
            else if (info2.status != null && status != null)
                if (!info2.status.equals(status)) status = info2.status;
            if (info2.largeText == null && largeText != null) largeText = null;
            else if (info2.largeText != null && largeText != null)
                if (!info2.largeText.equals(largeText)) largeText = info2.largeText;
            if (info2.smallText == null && smallText != null) smallText = null;
            else if (info2.smallText != null && smallText != null)
                if (!info2.smallText.equals(largeText)) smallText = info2.smallText;

            return new PresenceInfo(
                    presenceTypeCode,
                    startTimeStamp,
                    endTimeStamp,
                    largeImage,
                    largeText,
                    smallImage,
                    smallText,
                    details,
                    status,
                    state,
                    name,
                    button1,
                    button2
            );
        }

        public PresenceInfo setStatus(String status) {
            this.status = status;
            return this;
        }

        public static class Button implements Serializable {

            @SerializedName("label")
            public final String label;
            @SerializedName("url")
            public final String url;

            public Button(@NonNull String label, @NonNull String url) {
                this.label = label;
                this.url = url;
            }

            @Override
            public boolean equals(@Nullable Object obj) {
                if (obj == null) return false;
                if (obj instanceof Button) {
                    Button button = (Button) obj;
                    return button.label.equals(label) && button.url.equals(url);
                }
                return false;
            }
        }
    }

    public enum PresenceType {
        GAME,
        STREAMING,
        LISTEN,
        WATCH,
        STATUS,
        COMPETES
    }

    public static class UserStatus {
        public static final String ONLINE = "Wg4KCAoGb25saW5lGgIIAQ==";
        public static final String IDLE = "WgwKBgoEaWRsZRoCCAE=";
        public static final String DO_NOT_DISTURB = "WgsKBQoDZG5kGgIIAQ==";
        public static final String INVISIBLE = "WhEKCwoJaW52aXNpYmxlGgIIAQ==";
    }

    public static class User {
        @Nullable
        public final String discriminator;
        @NonNull
        public final String username;

        public User(
                @Nullable String discriminator,
                @NonNull String username
        ) {
            this.discriminator = discriminator;
            this.username = username;
        }
    }


}
