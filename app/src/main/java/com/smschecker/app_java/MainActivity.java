package com.smschecker.app_java;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ToggleButton;
import android.Manifest;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity implements SmsListener {
    AlertDialog.Builder builder;
    ListView logsView;
    ArrayList<String> logs = new ArrayList<>();
    String token = "";
    ToggleButton workToggle;
    Button linkBtn;
    private CacheManager tokenCache;
    ScheduledExecutorService executor;
    SmsReceiver receiver;
    public String api_base = "http://your_domain:8080/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checkAndRequestPermissions();

        logsView = findViewById(R.id.logs);
        builder = new AlertDialog.Builder(this);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, logs);
        logsView.setAdapter(adapter);

        workToggle = findViewById(R.id.workToggle);
        workToggle.setEnabled(false);
        workToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    startOnlineUpdater();
                } else {
                    stopOnlineUpdater();
                }
            }
        });

        linkBtn = findViewById(R.id.linkButton);

        tokenCache = new CacheManager(this, "token");

        if(tokenCache.exists()) {
            token = tokenCache.get();

            EditText tokenInput = findViewById(R.id.tokenInput);
            tokenInput.setText(token);

            writeLog("Восстановили токен из кеша");
        }

        executor = Executors.newScheduledThreadPool(1);

        receiver = new SmsReceiver();
        receiver.setSmsListener(this);
        IntentFilter intentFilter = new IntentFilter("android.provider.Telephony.SMS_RECEIVED");
        registerReceiver(receiver, intentFilter);

        createNotificationChannel();

        if (!NotificationManagerCompat.getEnabledListenerPackages(this).contains(getPackageName())) {
            startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "SmsChecker Channel";
            String description = "Channel for Foreground Service";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel("smschecker_pro", name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void checkAndRequestPermissions() {
        String[] permissions = {
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.VIBRATE,
                Manifest.permission.FOREGROUND_SERVICE,
                Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC,
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.INTERNET,
                Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.READ_PHONE_STATE
        };

        ArrayList<String> permissionsToRequest = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toArray(new String[0]), 0);
        }
    }

    private void startOnlineUpdater() {
        Intent updateOnlineService = new Intent(this, updateOnlineService.class);
        ContextCompat.startForegroundService(this, updateOnlineService);
    }

    private void stopOnlineUpdater() {
        Intent updateOnlineService = new Intent(this, updateOnlineService.class);
        stopService(updateOnlineService);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(receiver);
    }

    @Override
    public void onSmsReceived(String sender, String text) {
        if(!workToggle.isChecked()) return;

        writeLog("Получена SMS от " + sender + " с текстом " + text);

        JSONObject json = new JSONObject();

        try {
            json.put("token", token);
            json.put("sender", sender);
            json.put("message", text);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        tokenCache.set(token);

        String result = json.toString();

        sendPostRequest(api_base + "device-api/register-sms", result, new PostRequestCallback() {
            @Override
            public void onResult(String result) {
                writeLog("Результат регистрации СМС: " + token);
            }

            @Override
            public void onError(Exception e) {
                writeLog("Произошла ошибка при регистрации СМС: " + e.toString());
            }
        });
    }

    public void writeLog(String str) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                logs.add(str);
                ((ArrayAdapter)logsView.getAdapter()).notifyDataSetChanged();
            }
        });
    }

    public void linkAction(View view) throws JSONException {
        EditText tokenInput = findViewById(R.id.tokenInput);
        token = tokenInput.getText().toString();

        writeLog("Установлен токен " + token);

        JSONObject json = new JSONObject();
        json.put("token", token);
        tokenCache.set(token);

        String result = json.toString();

        sendPostRequest(api_base + "device-api/verify", result, new PostRequestCallback() {
            @Override
            public void onResult(String result) {
                if(Objects.equals(result, "\"OK\"")) {
                    writeLog("Токен корректный, привязка выполнена");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tokenInput.setEnabled(false);
                            linkBtn.setEnabled(false);
                            workToggle.setEnabled(true);
                        }
                    });

                } else writeLog("При проверке токена произошла ошибка: " + result);
            }

            @Override
            public void onError(Exception e) {
                writeLog("Произошла ошибка при проверке токена: " + e.toString());
            }
        });
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
