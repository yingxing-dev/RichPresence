package com.pride.x.rich.presence.auth;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.pride.x.rich.presence.account.DiscordUser;
import com.pride.x.rich.presence.auth.cookies.DiscordCookies;
import com.pride.x.rich.presence.auth.objects.Captcha;
import com.pride.x.rich.presence.auth.objects.LoginData;
import com.pride.x.rich.presence.auth.objects.LoginResponse;
import com.pride.x.rich.presence.auth.objects.Otp;
import com.pride.x.rich.presence.auth.objects.OtpData;
import com.pride.x.rich.presence.auth.objects.OtpResponse;
import com.pride.x.rich.presence.auth.objects.SmsData;
import com.pride.x.rich.presence.auth.objects.SmsResponse;
import com.pride.x.rich.presence.service.PresenceService;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class DiscordAuthorization {

    private static final int API_VERSION = 9;

    private static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36";
    private static final String AUTH_BASE_URL = "https://discord.com/api/v" + API_VERSION + "/auth/";

    private final AuthorizationCallback callback;
    private final Context context;

    // connection client
    private final OkHttpClient client;

    // auth cache data
    private boolean sms_otp = false;

    public DiscordAuthorization(@NonNull Context context, @Nullable AuthorizationCallback callback) {
        // clear cookies
        DiscordCookies.clearCookies(context);

        // create connection client
        this.client = new OkHttpClient.Builder()
                .followSslRedirects(true)
                .followRedirects(true)
                .cookieJar(
                        DiscordCookies.get(
                                context
                        )
                )
                .build();

        this.callback = callback;
        this.context = context;
    }

    private static Request.Builder addHeaderOnCreate(
            @NonNull Request.Builder builder,
            @SuppressWarnings("SameParameterValue") @NonNull String name,
            @Nullable String value
    ) {
        if (value != null && value.length() > 0)
            builder.addHeader(name, value);
        return builder;
    }

    public void login(@NonNull String username, @NonNull String password, @Nullable String captcha_token) {
        // generate json body
        String body = new GsonBuilder()
                .serializeNulls()
                .create()
                .toJson(
                        LoginData.create(
                                username,
                                password
                        )
                );

        Log.e(PresenceService.TAG, body);

        // clear ota flag
        sms_otp = false;

        // create content type
        MediaType type = MediaType.parse("application/json");

        // create request body
        RequestBody requestBody = RequestBody.create(body, type);

        // create request
        Request request = addHeaderOnCreate(new Request.Builder(), "X-Captcha-Key", captcha_token)
                .url(AUTH_BASE_URL + "login")
                .addHeader("User-Agent", USER_AGENT)
                .post(requestBody)
                .build();

        // execute
        Call call = client.newCall(request);
        call.enqueue(new Callback() {

            // main thread
            final Handler mainThread = new Handler(Looper.getMainLooper());

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (callback == null) return;
                // do in main thread
                mainThread.post(() -> {
                    // get exception message
                    String message = e.getMessage();
                    if (message == null) callback.onCallback(LoginState.CONNECTION_ERROR, null, null);
                    else {
                        // unknown error
                        if (message.equals("error_json_syntax")
                                || message.equals("unknown_error"))
                            callback.onCallback(LoginState.UNKNOWN_ERROR, null, null);
                        // invalid data
                        else if (message.equals("invalid_login_data"))
                            callback.onCallback(LoginState.INVALID_LOGIN, null, null);
                        // need otp
                        else if (message.startsWith("need_otp:"))
                        {
                            String[] opt_data = message.split(":");
                            callback.onCallback(LoginState.NEED_OTP, Otp.create(Boolean.parseBoolean(opt_data[2]), opt_data[1], null), null);
                        }
                    }
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                // create response instance
                LoginResponse loginResponse;
                // try parse data
                try {
                    ResponseBody responseBody = response.body();
                    if (responseBody == null) {
                        onFailure(call, new IOException("unknown_error"));
                        return;
                    }
                    String data = responseBody.string();
                    Log.e(PresenceService.TAG, data);
                    loginResponse = new Gson().fromJson(data, LoginResponse.class);
                }
                catch (JsonSyntaxException | IOException | NullPointerException e) {
                    e.printStackTrace();
                    onFailure(call, new IOException("error_json_syntax"));
                    return;
                }

                // if response if null
                if (loginResponse == null) {
                    onFailure(call, new IOException("unknown_error"));
                    return;
                }

                // if response error
                if (loginResponse.isError()) {
                    if (loginResponse.code == 50035) onFailure(call, new IOException("invalid_login_data"));
                    else onFailure(call, new IOException("unknown_error"));
                    return;
                }

                // if captcha required
                if (loginResponse.captcha_key != null && loginResponse.captcha_key.length > 0) {
                    String key = loginResponse.captcha_key[0];
                    if (key.equals("captcha-required")) {
                        // do in main thread
                        mainThread.post(() -> {
                            // if callback is null -> skip
                            if (callback == null) return;
                            // callback
                            callback.onCallback(
                                    LoginState.CAPTCHA_NEEDED,
                                    null,
                                    Captcha.create(
                                            loginResponse.captcha_service,
                                            loginResponse.captcha_sitekey
                                    )
                            );
                        });
                        return;
                    }
                }

                // if need otp
                if ((loginResponse.token == null || loginResponse.token.length() > 0) && loginResponse.ticket != null)
                {
                    onFailure(
                            call,
                            new IOException("need_otp:" + loginResponse.ticket + ":" + (loginResponse.sms ? "true" : "false"))
                    );
                    return;
                }

                // if too many requests
                if (loginResponse.retry_after >= 1) {
                    // do in main thread
                    mainThread.post(() -> {
                        // if callback is null -> skip
                        if (callback == null) return;
                        // callback
                        callback.onCallback(LoginState.OTP_TOO_MANY, null, null);
                    });
                }

                // if authorized
                if (loginResponse.token != null && loginResponse.token.length() > 0)
                {
                    // do in main thread
                    mainThread.post(() -> {
                        // save token
                        saveUser(loginResponse.token);
                        // if callback is null -> skip
                        if (callback == null) return;
                        // callback
                        callback.onCallback(LoginState.SUCCESS, null, null);
                    });
                }
            }
        });
    }

    private void saveUser(@NonNull String token) {
        DiscordUser.save(context, new DiscordUser(token));
    }

    public void otp(@NonNull Otp otp, @NonNull String code) {
        // generate json body
        String body = new GsonBuilder()
                .serializeNulls()
                .create()
                .toJson(
                        sms_otp ? OtpData.Sms.create(
                                otp.getTicket(),
                                code
                        ) : OtpData.Code.create(
                                otp.getTicket(),
                                code
                        )
                );

        Log.e(PresenceService.TAG, body);

        // create content type
        MediaType type = MediaType.parse("application/json");

        // create request body
        RequestBody requestBody = RequestBody.create(body, type);

        // create request
        Request request = new Request.Builder()
                .url(AUTH_BASE_URL + "mfa/" + (sms_otp ? "sms" : "totp"))
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Origin", "https://discord.com")
                .addHeader("Referer", "https://discord.com/login")
                .post(requestBody)
                .build();

        // execute
        client.newCall(request).enqueue(new Callback() {

            // main thread
            final Handler mainThread = new Handler(Looper.getMainLooper());

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (callback == null) return;
                // do in main thread
                mainThread.post(() -> {
                    // get exception message
                    String message = e.getMessage();
                    Log.e(PresenceService.TAG, message);

                    if (message == null) callback.onCallback(LoginState.CONNECTION_ERROR, otp, null);
                    else {
                        // unknown error
                        switch (message) {
                            case "error_json_syntax":
                            case "unknown_error":
                                callback.onCallback(LoginState.UNKNOWN_ERROR, otp, null);
                                break;
                            // invalid data
                            case "invalid_otp_data":
                                callback.onCallback(LoginState.INVALID_OTP, otp, null);
                                break;
                            // session expired
                            case "session_invalid":
                                callback.onCallback(LoginState.SESSION_INVALID, otp, null);
                                break;
                        }
                    }
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                // create response instance
                OtpResponse otpResponse;
                // try parse data
                try {
                    ResponseBody responseBody = response.body();
                    if (responseBody == null) {
                        onFailure(call, new IOException("unknown_error"));
                        return;
                    }
                    String data = responseBody.string();
                    Log.e(PresenceService.TAG, data);
                    otpResponse = new Gson().fromJson(data, OtpResponse.class);
                }
                catch (JsonSyntaxException | IOException | NullPointerException e) {
                    e.printStackTrace();
                    onFailure(call, new IOException("error_json_syntax"));
                    return;
                }

                // if response if null
                if (otpResponse == null) {
                    onFailure(call, new IOException("unknown_error"));
                    return;
                }

                // if too many requests
                if (otpResponse.retry_after >= 1) {
                    // do in main thread
                    mainThread.post(() -> {
                        // if callback is null -> skip
                        if (callback == null) return;
                        // callback
                        callback.onCallback(LoginState.OTP_TOO_MANY, otp, null);
                    });
                }

                // if response error
                if (otpResponse.isError()) {
                    if (otpResponse.code == 60008) onFailure(call, new IOException("invalid_otp_data"));
                    else if (otpResponse.code == 60006) onFailure(call, new IOException("session_invalid"));
                    else onFailure(call, new IOException("unknown_error"));
                    return;
                }

                // if authorized
                if (otpResponse.token != null && otpResponse.token.length() > 0)
                {
                    // do in main thread
                    mainThread.post(() -> {
                        // save token
                        saveUser(otpResponse.token);
                        // if callback is null -> skip
                        if (callback == null) return;
                        // callback
                        callback.onCallback(LoginState.SUCCESS, null, null);
                    });
                }
            }
        });
    }

    public void send_sms(@NonNull Otp otp) {
        // generate json body
        String body = new GsonBuilder()
                .serializeNulls()
                .create()
                .toJson(
                        SmsData.create(otp.getTicket())
                );

        Log.e(PresenceService.TAG, body);

        // create content type
        MediaType type = MediaType.parse("application/json");

        // create request body
        RequestBody requestBody = RequestBody.create(body, type);

        // create request
        Request request = new Request.Builder()
                .url(AUTH_BASE_URL + "mfa/sms/send")
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Origin", "https://discord.com")
                .addHeader("Referer", "https://discord.com/login")
                .post(requestBody)
                .build();

        // execute
        client.newCall(request).enqueue(new Callback() {

            // main thread
            final Handler mainThread = new Handler(Looper.getMainLooper());

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (callback == null) return;
                // do in main thread
                mainThread.post(() -> {
                    // get exception message
                    String message = e.getMessage();
                    Log.e(PresenceService.TAG, message);

                    if (message == null) callback.onCallback(LoginState.CONNECTION_ERROR, otp, null);
                    else {
                        // unknown error
                        if (message.equals("error_json_syntax")
                                || message.equals("unknown_error"))
                            callback.onCallback(LoginState.UNKNOWN_ERROR, otp, null);
                            // invalid data
                        else if (message.equals("session_invalid"))
                            callback.onCallback(LoginState.SESSION_INVALID, otp, null);
                    }
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                // create response instance
                SmsResponse smsResponse;
                // try parse data
                try {
                    ResponseBody responseBody = response.body();
                    if (responseBody == null) {
                        onFailure(call, new IOException("unknown_error"));
                        return;
                    }
                    String data = responseBody.string();
                    Log.e(PresenceService.TAG, data);
                    smsResponse = new Gson().fromJson(data, SmsResponse.class);
                }
                catch (JsonSyntaxException | IOException | NullPointerException e) {
                    e.printStackTrace();
                    onFailure(call, new IOException("error_json_syntax"));
                    return;
                }

                // if response if null
                if (smsResponse == null) {
                    onFailure(call, new IOException("unknown_error"));
                    return;
                }

                // if response error
                if (smsResponse.isError()) {
                    if (smsResponse.code == 60006) onFailure(call, new IOException("session_invalid"));
                    else onFailure(call, new IOException("unknown_error"));
                    return;
                }

                // if too many requests
                if (smsResponse.retry_after >= 1) {
                    // do in main thread
                    mainThread.post(() -> {
                        // if callback is null -> skip
                        if (callback == null) return;
                        // callback
                        callback.onCallback(LoginState.OTP_TOO_MANY, null, null);
                    });
                }

                // if code sent
                if (smsResponse.phone != null && smsResponse.phone.length() > 0)
                {
                    // do in main thread
                    mainThread.post(() -> {
                        // fix value
                        sms_otp = true;
                        // if callback is null -> skip
                        if (callback == null) return;
                        // callback
                        callback.onCallback(
                                LoginState.SMS_CODE_SENT,
                                Otp.create(true, otp.getTicket(), smsResponse.phone),
                                null
                        );
                    });
                }
            }
        });
    }

    public enum LoginState {
        INVALID_LOGIN,
        NEED_OTP,
        INVALID_OTP,
        SUCCESS,
        SMS_CODE_SENT,
        SESSION_INVALID,
        CAPTCHA_NEEDED,
        OTP_TOO_MANY,
        UNKNOWN_ERROR,
        CONNECTION_ERROR
    }

    public interface AuthorizationCallback {
        void onCallback(@NonNull LoginState state, @Nullable Otp otp, @Nullable Captcha captcha);
    }

}
