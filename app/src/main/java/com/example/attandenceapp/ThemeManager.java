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

    private static final String FAB_TAG = "theme_toggle_fab";

    public static void applyTheme(Activity activity) {

        ViewGroup rootView = activity.findViewById(android.R.id.content);

        if (rootView == null) {
            return;
        }

        if (rootView.findViewWithTag(FAB_TAG) != null) {
            return;
        }

        if (!(rootView instanceof FrameLayout)) {
            return;
        }

        final SharedPreferences prefs =
                activity.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);

        final boolean isDark = prefs.getBoolean("is_dark_mode", false);

        FloatingActionButton fab = new FloatingActionButton(activity);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );

        params.gravity = Gravity.BOTTOM | Gravity.END;
        params.setMargins(
                dp(activity, 16),
                dp(activity, 16),
                dp(activity, 16),
                dp(activity, 24)
        );

        fab.setLayoutParams(params);
        fab.setTag(FAB_TAG);

        if (isDark) {
            fab.setImageResource(R.drawable.ic_sun);
            fab.setContentDescription("Switch to light mode");
        } else {
            fab.setImageResource(R.drawable.ic_moon);
            fab.setContentDescription("Switch to dark mode");
        }

        fab.setOnClickListener(v -> {
            boolean newDarkMode = !isDark;

            prefs.edit()
                    .putBoolean("is_dark_mode", newDarkMode)
                    .apply();

            AppCompatDelegate.setDefaultNightMode(
                    newDarkMode
                            ? AppCompatDelegate.MODE_NIGHT_YES
                            : AppCompatDelegate.MODE_NIGHT_NO
            );

            activity.recreate();
        });

        ((FrameLayout) rootView).addView(fab);
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density);
    }
}