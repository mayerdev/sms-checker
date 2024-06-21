package com.smschecker.app_java;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class NotificationListener extends NotificationListenerService {
    String token = "";
    private CacheManager tokenCache;
    public String api_base = "http://your_domain:8080/";

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        String packageName = sbn.getPackageName();
        CharSequence ticker = sbn.getNotification().tickerText;
        CharSequence title = null;
        CharSequence text = null;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            title = sbn.getNotification().extras.getString("android.title");
            text = sbn.getNotification().extras.getString("android.text");
        }

        JSONObject json = new JSONObject();

        tokenCache = new CacheManager(this, "token");

        if(tokenCache.exists()) {
            token = tokenCache.get();
        }

        try {
            json.put("token", token);
            json.put("sender", packageName);
            json.put("title", title);
            json.put("message", text);
            json.put("is_push", true);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        tokenCache.set(token);

        String result = json.toString();

        sendPostRequest(api_base + "device-api/register-sms", result, new PostRequestCallback() {
            @Override
            public void onResult(String result) {
                if(!packageName.equals("com.smschecker.app_java")) {
                    cancelNotification(sbn.getKey());
                }
            }

            @Override
            public void onError(Exception e) {
                if(!packageName.equals("com.smschecker.app_java")) {
                    cancelNotification(sbn.getKey());
                }
            }
        });
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        Log.d("NotificationListener", "Уведомление удалено");
    }

    public void sendPostRequest(final String request_url, final String jsonInput, final PostRequestCallback callback) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;

                try {
                    URL url = new URL(request_url);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json; utf-8");
                    conn.setRequestProperty("Accept", "application/json");
                    conn.setDoOutput(true);

                    try(OutputStream os = conn.getOutputStream()) {
                        byte[] input = jsonInput.getBytes("utf-8");
                        os.write(input, 0, input.length);
                    }

                    StringBuilder response = new StringBuilder();
                    try(BufferedReader br = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), "utf-8"))) {
                        String responseLine = null;
                        while ((responseLine = br.readLine()) != null) {
                            response.append(responseLine.trim());
                        }
                    }

                    if(callback != null) {
                        callback.onResult(response.toString());
                    }
                } catch (Exception e) {
                    if(callback != null) {
                        callback.onError(e);
                    }
                } finally {
                    if(conn != null) {
                        conn.disconnect();
                    }
                }
            }
        });

        thread.start();
    }
}
