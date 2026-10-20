package com.example.attandenceapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;

public class Attendence extends AppCompatActivity {

    private LinearLayout container;
    private ScrollView scrollView;
    private TextView loadingText;
    private String cookies;
    private SharedPreferences prefs;

    private final List<String> updatedSubjects = new ArrayList<>();
    private int totalSubjects = 0;
    private int loadedSubjects = 0;
    private boolean updateBannerShown = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_attendence);

        // Global light/dark floating button
        ThemeManager.applyTheme(this);

        container = findViewById(R.id.container);
        scrollView = findViewById(R.id.scrollView);
        loadingText = findViewById(R.id.loadingText);

        prefs = getSharedPreferences("attendance_data", MODE_PRIVATE);

        // Get cookies passed from Selection
        cookies = getIntent().getStringExtra("cookies");
        Log.d("ATT_COOKIES", cookies == null ? "null" : cookies);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        boolean isOnline = isNetworkAvailable();
        boolean hasCache = prefs.contains("cache_dash_html");

        if (!isOnline) {
            if (hasCache) {
                showOfflineBanner();
                loadOfflineData();
            } else {
                loadingText.setText("No internet and no saved dashboard available.");
            }
        } else {
            if (cookies == null || cookies.isEmpty()) {
                loadingText.setText("Session expired. Please login again.");
            } else {
                fetchDashboard();
            }
        }
    }

    private void fetchDashboard() {
        new Thread(() -> {
            java.net.HttpURLConnection conn = null;
            java.io.BufferedReader reader = null;

            try {
                java.net.URL url = new java.net.URL("https://agclms.in/DashBoardStudent");
                conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.setRequestProperty("Cookie", cookies);
                conn.setInstanceFollowRedirects(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(conn.getInputStream())
                );

                StringBuilder html = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    html.append(line);
                }

                String dashboardHtml = html.toString();

                // Save dashboard HTML cache AFTER reading it
                prefs.edit()
                        .putString("cache_dash_html", dashboardHtml)
                        .apply();

                Log.d("DASHBOARD_HTML", dashboardHtml.substring(0, Math.min(500, dashboardHtml.length())));

                Document doc = Jsoup.parse(dashboardHtml);
                Elements attLinks = doc.select("a[href*=AttendanceReport]");
                Log.d("ATT_LINKS", "Found: " + attLinks.size());

                List<String[]> subjects = new ArrayList<>();

                for (Element link : attLinks) {
                    Element parentRow = link.parent() != null ? link.parent().parent() : null;
                    Element td = parentRow != null ? parentRow.select("td").first() : null;

                    String subjectName = td != null ? td.text().trim() : "Unknown";
                    if (subjectName.isEmpty()) {
                        subjectName = "Unknown";
                    }

                    String href = link.attr("href");
                    String attUrl;

                    if (href.startsWith("http")) {
                        attUrl = href;
                    } else if (href.startsWith("/")) {
                        attUrl = "https://agclms.in" + href;
                    } else {
                        attUrl = "https://agclms.in/" + href;
                    }

                    // Avoid duplicate subject entries
                    boolean exists = false;
                    for (String[] existing : subjects) {
                        if (existing[0].equals(subjectName)) {
                            exists = true;
                            break;
                        }
                    }

                    if (!exists) {
                        subjects.add(new String[]{subjectName, attUrl});
                        Log.d("SUBJECT", subjectName + " -> " + attUrl);
                    }
                }

                // Sort alphabetically
                subjects.sort((a, b) -> a[0].compareTo(b[0]));

                // IMPORTANT: set totalSubjects BEFORE starting threads
                totalSubjects = subjects.size();
                loadedSubjects = 0;
                updateBannerShown = false;
                updatedSubjects.clear();

                if (totalSubjects == 0) {
                    runOnUiThread(() -> loadingText.setText("No attendance subjects found."));
                    return;
                }

                for (String[] subject : subjects) {
                    fetchAttendance(subject[0], subject[1]);
                }

            } catch (Exception e) {
                Log.e("DASHBOARD_ERROR", e.toString());
                runOnUiThread(() -> loadingText.setText("Unable to load attendance. Check internet/login."));
            } finally {
                try {
                    if (reader != null) reader.close();
                } catch (Exception ignored) {
                }

                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();
    }

    private void fetchAttendance(String subjectName, String attUrl) {
        new Thread(() -> {
            java.net.HttpURLConnection conn = null;
            java.io.BufferedReader reader = null;

            try {
                java.net.URL url = new java.net.URL(attUrl);
                conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.setRequestProperty("Cookie", cookies);
                conn.setInstanceFollowRedirects(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(conn.getInputStream())
                );

                StringBuilder html = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    html.append(line);
                }

                String pageHtml = html.toString();

                // Save subject attendance HTML cache AFTER reading it
                String key = getSubjectKey(subjectName);
                prefs.edit()
                        .putString("cache_att_" + key, pageHtml)
                        .apply();

                parseAttendance(subjectName, pageHtml, false);

            } catch (Exception e) {
                Log.e("ATT_ERROR", subjectName + " : " + e.toString());

                runOnUiThread(() -> {
                    loadedSubjects++;

                    if (!updateBannerShown && loadedSubjects >= totalSubjects) {
                        updateBannerShown = true;
                        showUpdateBanner();
                    }

                    if (loadedSubjects >= totalSubjects) {
                        loadingText.setVisibility(View.GONE);
                        scrollView.setVisibility(View.VISIBLE);
                    }
                });
            } finally {
                try {
                    if (reader != null) reader.close();
                } catch (Exception ignored) {
                }

                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();
    }

    private void parseAttendance(String subjectName, String pageHtml, boolean offline) {
        try {
            Document doc = Jsoup.parse(pageHtml);
            Elements tables = doc.select("table");

            int lecturePresent = 0;
            int lectureAbsent = 0;
            int tutorialPresent = 0;
            int tutorialAbsent = 0;
            boolean hasTutorial = false;

            for (Element table : tables) {
                String header = table.select("th").text().toLowerCase();
                boolean isTutorial = header.contains("tutorial");

                Elements rows = table.select("tr");

                for (Element row : rows) {
                    Elements cols = row.select("td");

                    if (cols.size() >= 2) {
                        String status = cols.get(1).text().trim().toUpperCase();

                        if (isTutorial) {
                            hasTutorial = true;

                            if (status.equals("PRESENT")) {
                                tutorialPresent++;
                            } else if (status.equals("ABSENT")) {
                                tutorialAbsent++;
                            }
                        } else {
                            if (status.equals("PRESENT")) {
                                lecturePresent++;
                            } else if (status.equals("ABSENT")) {
                                lectureAbsent++;
                            }
                        }
                    }
                }
            }

            final int finalLP = lecturePresent;
            final int finalLA = lectureAbsent;
            final int finalTP = tutorialPresent;
            final int finalTA = tutorialAbsent;
            final boolean finalHasTutorial = hasTutorial;

            Log.d("ATT_DATA", subjectName +
                    " L:" + finalLP + "/" + (finalLP + finalLA) +
                    " T:" + finalTP + "/" + (finalTP + finalTA));

            runOnUiThread(() -> addSubjectCard(
                    subjectName,
                    finalLP,
                    finalLA,
                    finalTP,
                    finalTA,
                    finalHasTutorial,
                    offline
            ));

        } catch (Exception e) {
            Log.e("PARSE_ERROR", subjectName + " : " + e.toString());

            runOnUiThread(() -> {
                loadedSubjects++;

                if (!offline && !updateBannerShown && loadedSubjects >= totalSubjects) {
                    updateBannerShown = true;
                    showUpdateBanner();
                }

                if (loadedSubjects >= totalSubjects) {
                    loadingText.setVisibility(View.GONE);
                    scrollView.setVisibility(View.VISIBLE);
                }
            });
        }
    }

    private void loadOfflineData() {
        String dashHtml = prefs.getString("cache_dash_html", "");

        if (dashHtml.isEmpty()) {
            loadingText.setText("No saved dashboard found.");
            return;
        }

        Document doc = Jsoup.parse(dashHtml);
        Elements attLinks = doc.select("a[href*=AttendanceReport]");

        List<String> subjectNames = new ArrayList<>();

        for (Element link : attLinks) {
            Element parentRow = link.parent() != null ? link.parent().parent() : null;
            Element td = parentRow != null ? parentRow.select("td").first() : null;

            String subjectName = td != null ? td.text().trim() : "Unknown";
            if (subjectName.isEmpty()) {
                subjectName = "Unknown";
            }

            if (!subjectNames.contains(subjectName)) {
                subjectNames.add(subjectName);
            }
        }

        subjectNames.sort(String::compareTo);

        totalSubjects = subjectNames.size();
        loadedSubjects = 0;
        updateBannerShown = false;
        updatedSubjects.clear();

        if (totalSubjects == 0) {
            loadingText.setText("No saved attendance subjects found.");
            return;
        }

        for (String subjectName : subjectNames) {
            String key = getSubjectKey(subjectName);
            String cachedAttHtml = prefs.getString("cache_att_" + key, "");

            if (cachedAttHtml.isEmpty()) {
                loadedSubjects++;

                if (loadedSubjects >= totalSubjects) {
                    loadingText.setVisibility(View.GONE);
                    scrollView.setVisibility(View.VISIBLE);
                }

                continue;
            }

            parseAttendance(subjectName, cachedAttHtml, true);
        }
    }

    private void showOfflineBanner() {
        LinearLayout banner = new LinearLayout(this);
        banner.setOrientation(LinearLayout.HORIZONTAL);

        boolean dark = isDarkMode();
        banner.setBackgroundColor(dark ? 0xFF332700 : 0xFFFFF3CD);

        banner.setPadding(32, 24, 32, 24);

        LinearLayout.LayoutParams bannerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        bannerParams.setMargins(0, 0, 0, 24);
        banner.setLayoutParams(bannerParams);

        TextView text = new TextView(this);
        text.setText("📡 OFFLINE MODE: Showing last saved dashboard");
        text.setTextColor(dark ? 0xFFFFD54F : 0xFF664D00);
        text.setTextSize(14);
        text.setTypeface(null, android.graphics.Typeface.BOLD);

        banner.addView(text);

        container.addView(banner, 0);
    }

    private void addSubjectCard(
            String subjectName,
            int lecturePresent,
            int lectureAbsent,
            int tutorialPresent,
            int tutorialAbsent,
            boolean hasTutorial,
            boolean offline
    ) {
        loadingText.setVisibility(View.GONE);
        scrollView.setVisibility(View.VISIBLE);

        int totalPresent = lecturePresent + tutorialPresent;
        int totalAbsent = lectureAbsent + tutorialAbsent;
        int total = totalPresent + totalAbsent;

        double pct = total > 0 ? (totalPresent * 100.0 / total) : 0;

        String key = getSubjectKey(subjectName);

        // Only check updates and save fresh stats when online
        if (!offline) {
            int savedPresent = prefs.getInt(key + "_present", -1);
            int savedTotal = prefs.getInt(key + "_total", -1);

            if (savedPresent != -1 && (savedPresent != totalPresent || savedTotal != total)) {
                updatedSubjects.add(subjectName.toUpperCase());
            }

            prefs.edit()
                    .putInt(key + "_present", totalPresent)
                    .putInt(key + "_total", total)
                    .apply();
        }

        loadedSubjects++;

        if (!offline && !updateBannerShown && loadedSubjects >= totalSubjects) {
            updateBannerShown = true;
            showUpdateBanner();
        }

        String percentageStr = String.format("%.2f", pct) + "%";

        boolean dark = isDarkMode();

        int cardColor;
        if (pct < 75) {
            cardColor = dark ? 0xFF4A2020 : 0xFFFFEBEE;
        } else if (pct <= 85) {
            cardColor = dark ? 0xFF4A4520 : 0xFFFFF8E1;
        } else {
            cardColor = dark ? 0xFF204A2A : 0xFFE8F5E9;
        }

        int primaryText = dark ? 0xFFFFFFFF : 0xFF212121;
        int secondaryText = dark ? 0xFFCCCCCC : 0xFF616161;
        int dividerColor = dark ? 0xFF333333 : 0xFFE0E0E0;
        int okColor = dark ? 0xFF4CAF50 : 0xFF2E7D32;
        int badColor = dark ? 0xFFF44336 : 0xFFC62828;

        // Outer card
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(cardColor);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, 20);
        card.setLayoutParams(cardParams);
        card.setPadding(40, 36, 40, 36);

        // Subject name
        TextView nameView = new TextView(this);
        nameView.setText(subjectName);
        nameView.setTextColor(primaryText);
        nameView.setTextSize(20);
        nameView.setTypeface(null, android.graphics.Typeface.BOLD);
        nameView.setPadding(0, 0, 0, 16);
        card.addView(nameView);

        // Lecture row
        int lectureTotal = lecturePresent + lectureAbsent;
        TextView lectureView = new TextView(this);
        lectureView.setText("Lecture    : " + lecturePresent + "/" + lectureTotal);
        lectureView.setTextColor(secondaryText);
        lectureView.setTextSize(15);
        card.addView(lectureView);

        // Tutorial row, only if exists
        if (hasTutorial) {
            int tutorialTotal = tutorialPresent + tutorialAbsent;
            TextView tutorialView = new TextView(this);
            tutorialView.setText("Tutorial   : " + tutorialPresent + "/" + tutorialTotal);
            tutorialView.setTextColor(secondaryText);
            tutorialView.setTextSize(15);
            card.addView(tutorialView);
        }

        // Total row
        TextView totalView = new TextView(this);
        totalView.setText("Total      : " + totalPresent + "/" + total);
        totalView.setTextColor(secondaryText);
        totalView.setTextSize(15);
        totalView.setPadding(0, 0, 0, 12);
        card.addView(totalView);

        // Percentage
        TextView percentView = new TextView(this);
        percentView.setText("Percentage : " + percentageStr);
        percentView.setTextColor(primaryText);
        percentView.setTextSize(17);
        percentView.setTypeface(null, android.graphics.Typeface.BOLD);
        percentView.setPadding(0, 0, 0, 6);
        card.addView(percentView);

        // Status
        boolean eligible = pct >= 75;
        TextView statusView = new TextView(this);
        statusView.setText("Status : " + (eligible ? "Eligible ✓" : "Not Eligible ✗"));
        statusView.setTextColor(eligible ? okColor : badColor);
        statusView.setTextSize(15);
        statusView.setPadding(0, 0, 0, 16);
        card.addView(statusView);

        // Divider
        View divider = new View(this);
        divider.setBackgroundColor(dividerColor);

        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                2
        );
        divider.setLayoutParams(divParams);
        card.addView(divider);

        // Smart insight
        TextView insightView = new TextView(this);

        if (eligible) {
            int canMiss = (int) ((4.0 * totalPresent - 3.0 * total) / 3.0);
            canMiss = Math.max(0, canMiss);

            insightView.setText("💡 You can miss " + canMiss + " more classes");
            insightView.setTextColor(okColor);
        } else {
            int needAttend = (int) Math.ceil((75.0 * total - 100.0 * totalPresent) / 25.0);

            insightView.setText("⚠️ Attend " + needAttend + " more classes to be eligible");
            insightView.setTextColor(badColor);
        }

        insightView.setTextSize(13);
        insightView.setPadding(0, 12, 0, 0);
        card.addView(insightView);

        container.addView(card);
    }

    private void showUpdateBanner() {
        if (updatedSubjects.isEmpty()) {
            return;
        }

        String message;

        if (updatedSubjects.size() == 1) {
            message = updatedSubjects.get(0);
        } else if (updatedSubjects.size() == 2) {
            message = updatedSubjects.get(0) + " and " + updatedSubjects.get(1);
        } else {
            message = updatedSubjects.get(0) + ", " + updatedSubjects.get(1)
                    + " and " + (updatedSubjects.size() - 2) + " more";
        }

        boolean dark = isDarkMode();

        LinearLayout banner = new LinearLayout(this);
        banner.setOrientation(LinearLayout.HORIZONTAL);
        banner.setBackgroundColor(dark ? 0xFF1B3A2B : 0xFFE8F5E9);
        banner.setPadding(32, 24, 32, 24);

        LinearLayout.LayoutParams bannerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        bannerParams.setMargins(0, 0, 0, 24);
        banner.setLayoutParams(bannerParams);

        TextView icon = new TextView(this);
        icon.setText("✅ ");
        icon.setTextSize(20);
        banner.addView(icon);

        LinearLayout textLayout = new LinearLayout(this);
        textLayout.setOrientation(LinearLayout.VERTICAL);
        textLayout.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1
        ));

        TextView titleView = new TextView(this);
        titleView.setText("Attendance updated");
        titleView.setTextColor(dark ? 0xFFFFFFFF : 0xFF1B5E20);
        titleView.setTextSize(15);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView subjectsView = new TextView(this);
        subjectsView.setText(message);
        subjectsView.setTextColor(dark ? 0xFFCCCCCC : 0xFF33691E);
        subjectsView.setTextSize(13);

        textLayout.addView(titleView);
        textLayout.addView(subjectsView);
        banner.addView(textLayout);

        TextView closeBtn = new TextView(this);
        closeBtn.setText("✕");
        closeBtn.setTextColor(dark ? 0xFFCCCCCC : 0xFF616161);
        closeBtn.setTextSize(16);
        closeBtn.setPadding(16, 0, 0, 0);
        closeBtn.setOnClickListener(v -> container.removeView(banner));

        banner.addView(closeBtn);

        container.addView(banner, 0);
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        if (cm == null) {
            return false;
        }

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }

    private boolean isDarkMode() {
        int nightModeFlags =
                getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;

        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES;
    }

    private String getSubjectKey(String subjectName) {
        if (subjectName == null) {
            return "unknown";
        }

        return subjectName.trim().replaceAll("[^A-Za-z0-9]", "_");
    }
}