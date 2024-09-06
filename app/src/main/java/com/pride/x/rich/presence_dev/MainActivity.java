package com.pride.x.rich.presence_dev;

import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;
import com.hcaptcha.sdk.HCaptcha;
import com.hcaptcha.sdk.HCaptchaConfig;
import com.hcaptcha.sdk.HCaptchaSize;
import com.hcaptcha.sdk.HCaptchaTheme;
import com.pride.x.rich.presence.Presence;
import com.pride.x.rich.presence.account.DiscordUser;
import com.pride.x.rich.presence.auth.DiscordAuthorization;
import com.pride.x.rich.presence.auth.cookies.DiscordCookies;
import com.pride.x.rich.presence.service.PresenceService;
import com.pride.x.rich.presence.service.instance.PresenceInstance;

public class MainActivity extends AppCompatActivity {

    private DiscordAuthorization authorization = null;
    private PresenceInstance instance;

    String[] pass = {
      "Said230804"
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        authorization = new DiscordAuthorization(this, (status, otp, captcha) -> {
            Log.e(PresenceService.TAG, "" + status);
            if (status == DiscordAuthorization.LoginState.CAPTCHA_NEEDED) {
                final HCaptcha hCaptcha = HCaptcha.getClient(this);
                hCaptcha
                        .addOnSuccessListener(response -> {
                            String userResponseToken = response.getTokenResult();
                            authorization.login("<username>", "<password>", userResponseToken);
                        })
                        .addOnFailureListener(e -> {
                            Log.d("hCaptcha", "hCaptcha failed: " + e.getMessage() + "(" + e.getStatusCode() + ")");
                        })
                        .addOnOpenListener(() -> {
                            Log.d("hCaptcha", "hCaptcha is now visible.");
                        });

                assert captcha != null;
                HCaptchaConfig config = HCaptchaConfig.builder()
                        .siteKey(captcha.getSitekey())
                        .size(HCaptchaSize.NORMAL)
                        .theme(HCaptchaTheme.DARK)
                        .build();
                hCaptcha.setup(config).verifyWithHCaptcha();
            }
            if (status == DiscordAuthorization.LoginState.NEED_OTP) {
                Log.e(PresenceService.TAG, "" + otp.getTicket());
                Log.e(PresenceService.TAG, "" + otp.isSmsAvailable());
                Log.e(PresenceService.TAG, "" + otp.getPhone());
                authorization.otp(otp, "619407");
                // l7jv-n7mq
            }
            if (status == DiscordAuthorization.LoginState.SUCCESS)
            {
                Log.e("PresenceX", "Cookies: " + new Gson().toJson(DiscordCookies.getStore(this).getCookies()));
                startPresence();
            }
        });

        DiscordUser user = DiscordUser.read(this);
        if (user != null && !user.isExpired())
            startPresence();
        else
            authorization.login("alsumir623@gmail.com", pass[0], null);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    private void startPresence() {
        Presence.connect(this, "1107433617765974036", DiscordUser.read(this).getToken(), new PresenceService.PresenceCallback() {
            @Override
            public void onReady(@NonNull PresenceInstance instance, @NonNull Presence.User user) {
                MainActivity.this.instance = instance;

                Presence.PresenceInfo info = new Presence.PresenceInfo();
                info.startTimeStamp = System.currentTimeMillis();
                info.presenceType = Presence.PresenceType.GAME;
                info.name = getString(R.string.app_name);
                info.largeImage = "https://sun7-23.userapi.com/impg/wD5ViVac3PJ8VHNP-rOrY-ewA4qxOWooes3hpw/6zx9iJ2jc_k.jpg?size=400x400&quality=95&sign=2e7f78a39ffec1b43efb0f79b8e83be7&type=audio";
                info.largeText = "Large text";
                info.smallImage = "https://cdn.discordapp.com/app-icons/1107433617765974036/744847fc792bb54001acd3ff05cc9d43.png?size=512";
                info.smallText = "Small text";
                info.details = "Dev build";
                info.state = "Android development";
                info.button1 = new Presence.PresenceInfo.Button("Test 1", "https://twitch.tv/discord");
                info.button2 = new Presence.PresenceInfo.Button("Test 2", "https://vk.com/artist/hoyo_mix");
                info.status = Presence.UserStatus.DO_NOT_DISTURB;
                instance.setPresence(info, true);
            }

            @Override
            public void onDisconnected() {
                Log.e(PresenceService.TAG, "DISCONNECTED!");
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (instance != null) {
            instance.disconnect();
            instance = null;
        }
    }
}
