package com.smschecker.app_java;

public interface PostRequestCallback {
    void onResult(String result);
    void onError(Exception e);
}