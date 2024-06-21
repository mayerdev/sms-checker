package com.smschecker.app_java;

public interface SmsListener {
    void onSmsReceived(String sender, String text);
}