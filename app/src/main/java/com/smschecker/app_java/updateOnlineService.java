package com.smschecker.app_java;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class updateOnlineService extends Service {
    private CacheManager tokenCache;
    ScheduledExecutorService executor;
    public String token;
    public String api_base = "http://your_domain:8080/";

    Runnable updateOnline = new Runnable() {
        public void run() {
            token = tokenCache.get();

            JSONObject json = new JSONObject();

            try {
                json.put("token", token);
                JSONObject telemetry = new JSONObject();
                telemetry.put("android_sdk", Build.VERSION.SDK_INT);
                telemetry.put("android_release", Build.VERSION.RELEASE);
                JSONObject network = NetworkUtils.getNetworkInfo(getApplicationContext());
                telemetry.put("network", network);
                telemetry.put("device", Build.DEVICE);
                telemetry.put("manufacter", Build.MANUFACTURER);
                telemetry.put("id", Build.ID);
                telemetry.put("model", Build.MODEL);
                telemetry.put("board", Build.BOARD);
                telemetry.put("brand", Build.BRAND);
                telemetry.put("hardware", Build.HARDWARE);

                JSONArray grantedPermissions = PermissionUtils.getGrantedPermissions(getApplicationContext());
                telemetry.put("permissions", grantedPermissions);
                telemetry.put("push_read_allowed", NotificationAccessHelper.isNotificationServiceEnabled(getApplicationContext()));

                try{
                    String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                    telemetry.put("version", versionName);
                } catch (PackageManager.NameNotFoundException e) {
                    e.printStackTrace();
                }

                json.put("telemetry", telemetry);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }

            String result = json.toString();

            sendPostRequest(api_base + "device-api/online", result, new PostRequestCallback() {
                @Override
                public void onResult(String result) {
                    if(!Objects.equals(result, "\"OK\"")) {
                        System.out.println("Вы больше не онлайн, произошла ошибка, код: " + result);

                        executor.shutdown();
                    }
                }

                @Override
                public void onError(Exception e) {
                    System.out.println("Произошла ошибка при проверке токена: " + e.toString());
                }
            });
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        tokenCache = new CacheManager(this, "token");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @SuppressLint("ForegroundServiceType")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        executor = Executors.newScheduledThreadPool(1);
        executor.scheduleAtFixedRate(updateOnline, 0, 3, TimeUnit.SECONDS);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "smschecker_pro")
                .setContentTitle("SmsChecker Foreground Worker")
                .setContentText("Updating online...");

        startForeground(1, builder.build());

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public void sendPostRequest(final String request_url, final String jsonInput, final PostRequestCallback callback) {
        new Thread(new Runnable() {
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

                    try (OutputStream os = conn.getOutputStream()) {
                        byte[] input = jsonInput.getBytes("utf-8");
                        os.write(input, 0, input.length);
                    }

                    StringBuilder response = new StringBuilder();
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), "utf-8"))) {
                        String responseLine = null;
                        while ((responseLine = br.readLine()) != null) {
                            response.append(responseLine.trim());
                        }
                    }

                    if (callback != null) {
                        callback.onResult(response.toString());
                    }
                } catch (Exception e) {
                    if (callback != null) {
                        callback.onError(e);
                    }
                } finally {
                    if (conn != null) {
                        conn.disconnect();
                    }
                }
            }
        }).start();
    }
}
