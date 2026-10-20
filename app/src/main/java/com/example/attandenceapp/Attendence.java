package com.example.attandenceapp;

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

public class Attendence extends AppCompatActivity {

    private LinearLayout container;
    private ScrollView scrollView;
    private TextView loadingText;
    private String cookies;
    private android.content.SharedPreferences prefs;
    private java.util.List<String> updatedSubjects = new java.util.ArrayList<>();
    private int totalSubjects = 0;
    private int loadedSubjects = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_attendence);

        container = findViewById(R.id.container);
        scrollView = findViewById(R.id.scrollView);
        loadingText = findViewById(R.id.loadingText);
        prefs = getSharedPreferences("attendance_data", MODE_PRIVATE);
        // get cookies passed from Selection
        cookies = getIntent().getStringExtra("cookies");
        Log.d("ATT_COOKIES", cookies);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // start fetching
        fetchDashboard();
    }

    private void fetchDashboard() {
        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL("https://agclms.in/DashBoardStudent");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.setRequestProperty("Cookie", cookies);
                conn.setInstanceFollowRedirects(true);

                java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(conn.getInputStream())
                );
                StringBuilder html = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) html.append(line);
                reader.close();

                Log.d("DASHBOARD_HTML", html.toString().substring(0, 500));

                // parse subject attendance links
                Document doc = Jsoup.parse(html.toString());
                // find all Attendance links
                Elements attLinks = doc.select("a[href*=AttendanceReport]");
                Log.d("ATT_LINKS", "Found: " + attLinks.size());

                // replace the for loop in fetchDashboard() with this:
                java.util.List<String[]> subjects = new java.util.ArrayList<>();
                for (Element link : attLinks) {
                    String subjectName = link.parent().parent().select("td").first().text();
                    String attUrl = "https://agclms.in" + link.attr("href");
                    subjects.add(new String[]{subjectName, attUrl});
                    Log.d("SUBJECT", subjectName + " → " + attUrl);
                }

                // sort alphabetically
                subjects.sort((a, b) -> a[0].compareTo(b[0]));

                // now fetch each one
                for (String[] subject : subjects) {
                    fetchAttendance(subject[0], subject[1]);
                }

                totalSubjects = subjects.size();

            } catch (Exception e) {
                Log.e("DASHBOARD_ERROR", e.toString());
            }
        }).start();
    }

    private void fetchAttendance(String subjectName, String attUrl) {
        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL(attUrl);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.setRequestProperty("Cookie", cookies);
                conn.setInstanceFollowRedirects(true);

                java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(conn.getInputStream())
                );
                StringBuilder html = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) html.append(line);
                reader.close();

                Document doc = Jsoup.parse(html.toString());
                Elements tables = doc.select("table");

                int lecturePresent = 0, lectureAbsent = 0;
                int tutorialPresent = 0, tutorialAbsent = 0;
                boolean hasTutorial = false;

                for (Element table : tables) {
                    // check header to identify table type
                    String header = table.select("th").text().toLowerCase();
                    boolean isTutorial = header.contains("tutorial");

                    Elements rows = table.select("tr");
                    for (Element row : rows) {
                        Elements cols = row.select("td");
                        if (cols.size() >= 2) {
                            String status = cols.get(1).text().trim().toUpperCase();
                            if (isTutorial) {
                                hasTutorial = true;
                                if (status.equals("PRESENT")) tutorialPresent++;
                                else if (status.equals("ABSENT")) tutorialAbsent++;
                            } else {
                                if (status.equals("PRESENT")) lecturePresent++;
                                else if (status.equals("ABSENT")) lectureAbsent++;
                            }
                        }
                    }
                }

                Log.d("ATT_DATA", subjectName +
                        " L:" + lecturePresent + "/" + (lecturePresent+lectureAbsent) +
                        " T:" + tutorialPresent + "/" + (tutorialPresent+tutorialAbsent));

                int finalLP = lecturePresent, finalLA = lectureAbsent;
                int finalTP = tutorialPresent, finalTA = tutorialAbsent;
                boolean finalHasTutorial = hasTutorial;

                runOnUiThread(() -> addSubjectCard(
                        subjectName,
                        finalLP, finalLA,
                        finalTP, finalTA,
                        finalHasTutorial
                ));

            } catch (Exception e) {
                Log.e("ATT_ERROR", e.toString());
            }
        }).start();
    }

    private void addSubjectCard(String subjectName, int lecturePresent, int lectureAbsent,
                                int tutorialPresent, int tutorialAbsent, boolean hasTutorial) {

        loadingText.setVisibility(View.GONE);
        scrollView.setVisibility(View.VISIBLE);


        int totalPresent = lecturePresent + tutorialPresent;
        int totalAbsent = lectureAbsent + tutorialAbsent;
        int total = totalPresent + totalAbsent;
        double pct = total > 0 ? (totalPresent * 100.0 / total) : 0;

        // check if attendance changed since last time
        String key = subjectName.replaceAll("\\s+", "_");
        int savedPresent = prefs.getInt(key + "_present", -1);
        int savedTotal = prefs.getInt(key + "_total", -1);

        if (savedPresent != -1 && (savedPresent != totalPresent || savedTotal != total)) {
            updatedSubjects.add(subjectName.toUpperCase());
        }

// save current values
        prefs.edit()
                .putInt(key + "_present", totalPresent)
                .putInt(key + "_total", total)
                .apply();

// track loading progress
        loadedSubjects++;
        if (loadedSubjects >= totalSubjects) {
            showUpdateBanner();
        }


        String percentageStr = String.format("%.2f", pct) + "%";

        // outer card
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);

        int cardColor;
        if (pct < 75) {
            cardColor = 0xFF4A2020; // light red (dark-theme friendly)
        } else if (pct <= 85) {
            cardColor = 0xFF4A4520; // light yellow
        } else {
            cardColor = 0xFF204A2A; // light green
        }
        card.setBackgroundColor(cardColor);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, 20);
        card.setLayoutParams(cardParams);
        card.setPadding(40, 36, 40, 36);

        // subject name
        TextView nameView = new TextView(this);
        nameView.setText(subjectName);
        nameView.setTextColor(0xFFFFFFFF);
        nameView.setTextSize(20);
        nameView.setTypeface(null, android.graphics.Typeface.BOLD);
        nameView.setPadding(0, 0, 0, 16);
        card.addView(nameView);

        // lecture row
        int lectureTotal = lecturePresent + lectureAbsent;
        TextView lectureView = new TextView(this);
        lectureView.setText("Lecture    : " + lecturePresent + "/" + lectureTotal);
        lectureView.setTextColor(0xFFCCCCCC);
        lectureView.setTextSize(15);
        card.addView(lectureView);

        // tutorial row (only if exists)
        if (hasTutorial) {
            int tutorialTotal = tutorialPresent + tutorialAbsent;
            TextView tutorialView = new TextView(this);
            tutorialView.setText("Tutorial   : " + tutorialPresent + "/" + tutorialTotal);
            tutorialView.setTextColor(0xFFCCCCCC);
            tutorialView.setTextSize(15);
            card.addView(tutorialView);
        }

        // total row
        TextView totalView = new TextView(this);
        totalView.setText("Total      : " + totalPresent + "/" + total);
        totalView.setTextColor(0xFFCCCCCC);
        totalView.setTextSize(15);
        totalView.setPadding(0, 0, 0, 12);
        card.addView(totalView);

        // percentage
        TextView percentView = new TextView(this);
        percentView.setText("Percentage : " + percentageStr);
        percentView.setTextColor(0xFFFFFFFF);
        percentView.setTextSize(17);
        percentView.setTypeface(null, android.graphics.Typeface.BOLD);
        percentView.setPadding(0, 0, 0, 6);
        card.addView(percentView);

        // status

        boolean eligible = pct >= 75;
        TextView statusView = new TextView(this);
        statusView.setText("Status : " + (eligible ? "Eligible ✓" : "Not Eligible ✗"));
        statusView.setTextColor(eligible ? 0xFF4CAF50 : 0xFFF44336);
        statusView.setTextSize(15);
        statusView.setPadding(0, 0, 0, 16);
        card.addView(statusView);

        // divider
        View divider = new View(this);
        divider.setBackgroundColor(0xFF333333);
        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 2
        );
        divider.setLayoutParams(divParams);
        card.addView(divider);

        // smart insight
        TextView insightView = new TextView(this);
        if (eligible) {
            int canMiss = (int) ((4.0 * totalPresent - 3.0 * total) / 3.0);
            canMiss = Math.max(0, canMiss);
            insightView.setText("💡 You can miss " + canMiss + " more classes");
            insightView.setTextColor(0xFF4CAF50);
        } else {
            int needAttend = (int) Math.ceil((75.0 * total - 100.0 * totalPresent) / 25.0);
            insightView.setText("⚠️ Attend " + needAttend + " more classes to be eligible");
            insightView.setTextColor(0xFFF44336);
        }
        insightView.setTextSize(13);
        insightView.setPadding(0, 12, 0, 0);
        card.addView(insightView);

        container.addView(card);
    }

    private void showUpdateBanner() {
        if (updatedSubjects.isEmpty()) return;

        // build message
        String message;
        if (updatedSubjects.size() == 1) {
            message = updatedSubjects.get(0);
        } else if (updatedSubjects.size() == 2) {
            message = updatedSubjects.get(0) + " and " + updatedSubjects.get(1);
        } else {
            message = updatedSubjects.get(0) + ", " + updatedSubjects.get(1)
                    + " and " + (updatedSubjects.size() - 2) + " more";
        }

        // create banner
        LinearLayout banner = new LinearLayout(this);
        banner.setOrientation(LinearLayout.HORIZONTAL);
        banner.setBackgroundColor(0xFF1B3A2B);
        banner.setPadding(32, 24, 32, 24);
        LinearLayout.LayoutParams bannerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        bannerParams.setMargins(0, 0, 0, 24);
        banner.setLayoutParams(bannerParams);

        // green check icon text
        TextView icon = new TextView(this);
        icon.setText("✅ ");
        icon.setTextSize(20);
        banner.addView(icon);

        // text content
        LinearLayout textLayout = new LinearLayout(this);
        textLayout.setOrientation(LinearLayout.VERTICAL);
        textLayout.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView titleView = new TextView(this);
        titleView.setText("Attendance updated");
        titleView.setTextColor(0xFFFFFFFF);
        titleView.setTextSize(15);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView subjectsView = new TextView(this);
        subjectsView.setText(message);
        subjectsView.setTextColor(0xFFCCCCCC);
        subjectsView.setTextSize(13);

        textLayout.addView(titleView);
        textLayout.addView(subjectsView);
        banner.addView(textLayout);

        // X close button
        TextView closeBtn = new TextView(this);
        closeBtn.setText("✕");
        closeBtn.setTextColor(0xFFCCCCCC);
        closeBtn.setTextSize(16);
        closeBtn.setOnClickListener(v -> container.removeView(banner));
        banner.addView(closeBtn);

        // add banner at TOP of container
        container.addView(banner, 0);
    }
}