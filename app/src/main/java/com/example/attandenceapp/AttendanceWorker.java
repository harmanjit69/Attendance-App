package com.example.attandenceapp;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AttendanceWorker extends Worker {

    private static final String CHANNEL_ID = "attendance_updates";
    private static final int NOTIFICATION_ID = 1001;

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/120.0.0.0 Safari/537.36";

    public AttendanceWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {

        SharedPreferences loginPrefs =
                getApplicationContext().getSharedPreferences("login_data", Context.MODE_PRIVATE);

        String roll = loginPrefs.getString("roll", "");
        String pass = loginPrefs.getString("pass", "");

        if (roll == null || pass == null || roll.isEmpty() || pass.isEmpty()) {
            return Result.failure();
        }

        try {
            java.net.CookieManager cookieManager =
                    new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL);

            java.net.CookieHandler.setDefault(cookieManager);

            Map<String, String> cookieJar = new LinkedHashMap<>();

            String loginPage = request(
                    "https://agclms.in/Elogin/StudentLogin",
                    "GET",
                    null,
                    cookieJar,
                    cookieManager,
                    null,
                    null
            );

            if (loginPage == null) {
                return Result.retry();
            }

            Document loginDoc = Jsoup.parse(loginPage);
            Element tokenEl = loginDoc.select("input[name=__RequestVerificationToken]").first();

            if (tokenEl == null) {
                return Result.retry();
            }

            String csrfToken = tokenEl.attr("value");

            String postData =
                    "StudentId=" + URLEncoder.encode(roll, "UTF-8")
                            + "&Password=" + URLEncoder.encode(pass, "UTF-8")
                            + "&__RequestVerificationToken=" + URLEncoder.encode(csrfToken, "UTF-8");

            String postResponse = request(
                    "https://agclms.in/Elogin/StudentLogin",
                    "POST",
                    postData,
                    cookieJar,
                    cookieManager,
                    "https://agclms.in/Elogin/StudentLogin",
                    "https://agclms.in"
            );

            if (postResponse == null) {
                return Result.retry();
            }

            Document postDoc = Jsoup.parse(postResponse);

            if (postDoc.select("input[name=__RequestVerificationToken]").first() != null) {
                return Result.failure();
            }

            String dashboardHtml = request(
                    "https://agclms.in/DashBoardStudent",
                    "GET",
                    null,
                    cookieJar,
                    cookieManager,
                    "https://agclms.in/Elogin/StudentLogin",
                    null
            );

            if (dashboardHtml == null) {
                return Result.retry();
            }

            SharedPreferences attendancePrefs =
                    getApplicationContext().getSharedPreferences("attendance_data", Context.MODE_PRIVATE);

            attendancePrefs.edit()
                    .putString("cache_dash_html", dashboardHtml)
                    .apply();

            Document dashboardDoc = Jsoup.parse(dashboardHtml);
            Elements attendanceLinks = dashboardDoc.select("a[href*=AttendanceReport]");

            List<String[]> subjects = new ArrayList<>();

            for (Element link : attendanceLinks) {
                Element row = link.parent() != null ? link.parent().parent() : null;
                Element td = row != null ? row.select("td").first() : null;

                String subjectName = td != null ? td.text().trim() : "Unknown";

                if (subjectName.isEmpty()) {
                    subjectName = "Unknown";
                }

                String href = link.attr("href");
                String attendanceUrl;

                if (href.startsWith("http")) {
                    attendanceUrl = href;
                } else if (href.startsWith("/")) {
                    attendanceUrl = "https://agclms.in" + href;
                } else {
                    attendanceUrl = "https://agclms.in/" + href;
                }

                boolean alreadyAdded = false;

                for (String[] existing : subjects) {
                    if (existing[0].equals(subjectName)) {
                        alreadyAdded = true;
                        break;
                    }
                }

                if (!alreadyAdded) {
                    subjects.add(new String[]{subjectName, attendanceUrl});
                }
            }

            subjects.sort((a, b) -> a[0].compareTo(b[0]));

            SharedPreferences workerPrefs =
                    getApplicationContext().getSharedPreferences(
                            "attendance_worker_data",
                            Context.MODE_PRIVATE
                    );

            List<String> updates = new ArrayList<>();

            for (String[] subject : subjects) {
                String subjectName = subject[0];
                String subjectUrl = subject[1];

                String subjectHtml = request(
                        subjectUrl,
                        "GET",
                        null,
                        cookieJar,
                        cookieManager,
                        "https://agclms.in/DashBoardStudent",
                        null
                );

                if (subjectHtml == null) {
                    continue;
                }

                String key = getSubjectKey(subjectName);

                attendancePrefs.edit()
                        .putString("cache_att_" + key, subjectHtml)
                        .apply();

                AttendanceStats stats = parseAttendanceStats(subjectHtml);

                int totalPresent = stats.lecturePresent + stats.tutorialPresent;
                int totalAbsent = stats.lectureAbsent + stats.tutorialAbsent;
                int total = totalPresent + totalAbsent;

                double percentage = total > 0 ? (totalPresent * 100.0 / total) : 0;

                int savedPresent = workerPrefs.getInt(key + "_present", -1);
                int savedTotal = workerPrefs.getInt(key + "_total", -1);

                if (savedPresent == -1 && attendancePrefs.contains(key + "_present")) {
                    savedPresent = attendancePrefs.getInt(key + "_present", -1);
                    savedTotal = attendancePrefs.getInt(key + "_total", -1);
                }

                if (savedPresent != -1 && (savedPresent != totalPresent || savedTotal != total)) {
                    String message =
                            subjectName.toUpperCase(Locale.getDefault())
                                    + ": "
                                    + String.format(Locale.getDefault(), "%.2f", percentage)
                                    + "% ("
                                    + totalPresent + "/" + total
                                    + ")";

                    updates.add(message);
                }

                workerPrefs.edit()
                        .putInt(key + "_present", totalPresent)
                        .putInt(key + "_total", total)
                        .apply();
            }

            if (!updates.isEmpty()) {
                sendNotification(updates);
            }

            return Result.success();

        } catch (Exception e) {
            e.printStackTrace();
            return Result.retry();
        }
    }

    private String request(
            String urlString,
            String method,
            String body,
            Map<String, String> cookieJar,
            java.net.CookieManager cookieManager,
            String referer,
            String origin
    ) throws Exception {

        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod(method);
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty(
                "Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        );

        if (!cookieJar.isEmpty()) {
            conn.setRequestProperty("Cookie", cookieMapToString(cookieJar));
        }

        if (referer != null) {
            conn.setRequestProperty("Referer", referer);
        }

        if (origin != null) {
            conn.setRequestProperty("Origin", origin);
        }

        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);

        if ("POST".equalsIgnoreCase(method)) {
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setDoOutput(true);

            OutputStream os = conn.getOutputStream();
            os.write(body.getBytes("UTF-8"));
            os.flush();
            os.close();
        }

        int responseCode = conn.getResponseCode();

        Map<String, List<String>> headers = conn.getHeaderFields();

        if (headers != null) {
            updateCookieJar(cookieJar, headers.get("Set-Cookie"));
        }

        if (cookieManager != null) {
            for (HttpCookie c : cookieManager.getCookieStore().getCookies()) {
                cookieJar.put(c.getName(), c.getValue());
            }
        }

        InputStream inputStream = responseCode >= 400
                ? conn.getErrorStream()
                : conn.getInputStream();

        if (inputStream == null) {
            conn.disconnect();
            return null;
        }

        BufferedReader reader =
                new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));

        StringBuilder response = new StringBuilder();

        String line;

        while ((line = reader.readLine()) != null) {
            response.append(line);
        }

        reader.close();
        conn.disconnect();

        if (responseCode >= 400) {
            return null;
        }

        return response.toString();
    }

    private void updateCookieJar(Map<String, String> jar, List<String> setCookies) {
        if (setCookies == null) {
            return;
        }

        for (String setCookie : setCookies) {
            if (setCookie == null || setCookie.isEmpty()) {
                continue;
            }

            String pair = setCookie.split(";")[0].trim();
            int equalsIndex = pair.indexOf('=');

            if (equalsIndex > 0) {
                String name = pair.substring(0, equalsIndex).trim();
                String value = pair.substring(equalsIndex + 1).trim();

                jar.put(name, value);
            }
        }
    }

    private String cookieMapToString(Map<String, String> map) {
        StringBuilder sb = new StringBuilder();

        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (sb.length() > 0) {
                sb.append("; ");
            }

            sb.append(entry.getKey())
                    .append("=")
                    .append(entry.getValue());
        }

        return sb.toString();
    }

    private AttendanceStats parseAttendanceStats(String html) {
        AttendanceStats stats = new AttendanceStats();

        Document doc = Jsoup.parse(html);
        Elements tables = doc.select("table");

        for (Element table : tables) {
            String header = table.select("th").text().toLowerCase(Locale.getDefault());
            boolean isTutorial = header.contains("tutorial");

            Elements rows = table.select("tr");

            for (Element row : rows) {
                Elements cols = row.select("td");

                if (cols.size() >= 2) {
                    String status = cols.get(1).text().trim().toUpperCase(Locale.getDefault());

                    if (isTutorial) {
                        stats.hasTutorial = true;

                        if (status.equals("PRESENT")) {
                            stats.tutorialPresent++;
                        } else if (status.equals("ABSENT")) {
                            stats.tutorialAbsent++;
                        }
                    } else {
                        if (status.equals("PRESENT")) {
                            stats.lecturePresent++;
                        } else if (status.equals("ABSENT")) {
                            stats.lectureAbsent++;
                        }
                    }
                }
            }
        }

        return stats;
    }

    private String getSubjectKey(String subjectName) {
        if (subjectName == null) {
            return "unknown";
        }

        return subjectName.trim().replaceAll("[^A-Za-z0-9]", "_");
    }

    private void sendNotification(List<String> updates) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    getApplicationContext(),
                    Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        NotificationManager notificationManager =
                (NotificationManager) getApplicationContext()
                        .getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager == null) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Attendance Updates",
                    NotificationManager.IMPORTANCE_HIGH
            );

            channel.setDescription("Notifications when college attendance is updated");

            notificationManager.createNotificationChannel(channel);
        }

        StringBuilder messageBuilder = new StringBuilder();

        for (int i = 0; i < updates.size(); i++) {
            messageBuilder.append(updates.get(i));

            if (i < updates.size() - 1) {
                messageBuilder.append("\n");
            }
        }

        String fullMessage = messageBuilder.toString();

        String shortMessage = fullMessage.length() > 80
                ? fullMessage.substring(0, 77) + "..."
                : fullMessage;

        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingIntentFlags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(
                getApplicationContext(),
                0,
                intent,
                pendingIntentFlags
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                getApplicationContext(),
                CHANNEL_ID
        )
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Attendance Updated")
                .setContentText(shortMessage)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(fullMessage))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    private static class AttendanceStats {
        int lecturePresent = 0;
        int lectureAbsent = 0;
        int tutorialPresent = 0;
        int tutorialAbsent = 0;
        boolean hasTutorial = false;
    }
}