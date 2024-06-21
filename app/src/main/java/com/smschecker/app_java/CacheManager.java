package com.smschecker.app_java;

import android.content.Context;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class CacheManager {
    private File file;

    public CacheManager(Context context, String filename) {
        file = new File(context.getFilesDir(), filename);
    }

    public void set(String value) {
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(file));
            writer.write(value);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String get() {
        StringBuilder stringBuilder = new StringBuilder();
        try {
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return stringBuilder.toString();
    }

    public boolean exists() {
        return file.exists();
    }
}