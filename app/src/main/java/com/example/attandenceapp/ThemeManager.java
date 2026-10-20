package com.example.attandenceapp;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class ThemeManager {
    public static void applyTheme(Activity activity) {
        SharedPreferences prefs = activity.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        boolean isDark = prefs.getBoolean("is_dark_mode", false);

        // Apply global theme
        AppCompatDelegate.setDefaultNightMode(isDark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);

        // Inject Floating Action Button (FAB)
        ViewGroup contentView = activity.findViewById(android.R.id.content);
        if (contentView instanceof FrameLayout) {
            FloatingActionButton fab = new FloatingActionButton(activity);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.gravity = Gravity.BOTTOM | Gravity.END;
            params.setMargins(16, 16, 32, 32);
            fab.setLayoutParams(params);

            // Use system icons (or replace with your own R.drawable.ic_sun / ic_moon)
            fab.setImageResource(isDark ? android.R.drawable.ic_menu_view : android.R.drawable.ic_menu_edit);

            fab.setOnClickListener(v -> {
                prefs.edit().putBoolean("is_dark_mode", !isDark).apply();
                activity.recreate(); // Refresh the activity to apply new theme
            });
            contentView.addView(fab);
        }
    }
}