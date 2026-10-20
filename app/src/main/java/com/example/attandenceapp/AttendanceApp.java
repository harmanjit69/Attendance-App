package com.example.attandenceapp;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

public class AttendanceApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        SharedPreferences prefs =
                getSharedPreferences("app_prefs", Context.MODE_PRIVATE);

        boolean isDark = prefs.getBoolean("is_dark_mode", false);

        AppCompatDelegate.setDefaultNightMode(
                isDark
                        ? AppCompatDelegate.MODE_NIGHT_YES
                        : AppCompatDelegate.MODE_NIGHT_NO
        );
    }
}