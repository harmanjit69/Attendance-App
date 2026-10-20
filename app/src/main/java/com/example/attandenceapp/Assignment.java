package com.example.attandenceapp;

import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class Assignment extends AppCompatActivity {

    private LinearLayout container;
    private ScrollView scrollView;
    private TextView loadingText;
    private String cookies;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_assignment);

        container = findViewById(R.id.container);
        scrollView = findViewById(R.id.scrollView);
        loadingText = findViewById(R.id.loadingText);

        cookies = getIntent().getStringExtra("cookies");
        Log.d("ASSIGN_COOKIES", "Got cookies: " + cookies);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        fetchDashboard();
    }

    private java.net.HttpURLConnection openConn(String targetUrl, String referer) throws Exception {
        java.net.URL url = new java.net.URL(targetUrl);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36");
        conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        conn.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
        conn.setRequestProperty("Cookie", cookies);
        conn.setRequestProperty("Referer", referer);
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        return conn;
    }

    private synchronized void absorbCookies(java.net.HttpURLConnection conn) {
        java.util.List<String> setCookies = conn.getHeaderFields().get("Set-Cookie");
        if (setCookies == null || setCookies.isEmpty()) return;
        java.util.Map<String, String> map = new java.util.LinkedHashMap<>();
        if (cookies != null && !cookies.isEmpty()) {
            for (String p : cookies.split(";")) {
                p = p.trim();
                int eq = p.indexOf('=');
                if (eq > 0) map.put(p.substring(0, eq).trim(), p.substring(eq + 1).trim());
            }
        }
        for (String sc : setCookies) {
            String pair = sc.split(";")[0].trim();
            int eq = pair.indexOf('=');
            if (eq > 0) map.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
        }
        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<String, String> e : map.entrySet()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(e.getKey()).append("=").append(e.getValue());
        }
        cookies = sb.toString();
    }

    /**
     * Safely resolve a scraped href into an absolute URL.
     * Handles: already-absolute URLs, protocol-relative, root-relative, and relative paths.
     */
    private String resolveUrl(String href, String pageUrl) {
        if (href == null || href.trim().isEmpty()) return "";
        href = href.trim();

        // Already absolute
        if (href.startsWith("http://") || href.startsWith("https://")) return href;

        // Protocol-relative: //example.com/path
        if (href.startsWith("//")) return "https:" + href;

        // Root-relative: /path
        if (href.startsWith("/")) return "https://agclms.in" + href;

        // Relative to page: resolve against pageUrl base
        try {
            java.net.URI base = new java.net.URI(pageUrl);
            return base.resolve(href).toString();
        } catch (Exception e) {
            return "https://agclms.in/" + href;
        }
    }

    private String readBody(java.net.HttpURLConnection conn) throws Exception {
        absorbCookies(conn);
        java.io.BufferedReader r = new java.io.BufferedReader(
                new java.io.InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();
        return sb.toString();
    }

    private void fetchDashboard() {
        new Thread(() -> {
            try {
                java.net.HttpURLConnection conn = openConn(
                        "https://agclms.in/DashBoardStudent",
                        "https://agclms.in/Elogin/StudentLogin");
                int code = conn.getResponseCode();
                String finalUrl = conn.getURL().toString();
                Log.d("DASHBOARD", "Code=" + code + " FinalURL=" + finalUrl);

                String html = readBody(conn);
                conn.disconnect();

                Log.d("DASHBOARD_HTML", html.substring(0, Math.min(300, html.length())));

                Document doc = Jsoup.parse(html);

                if (doc.select("input[name=__RequestVerificationToken]").first() != null) {
                    Log.e("DASHBOARD", "SESSION INVALID");
                    runOnUiThread(() -> {
                        loadingText.setText("Session expired. Please login again.");
                        Toast.makeText(Assignment.this,
                                "Session invalid — please login again", Toast.LENGTH_LONG).show();
                    });
                    return;
                }

                Elements assignLinks = doc.select("a[href*=DownloadAssignment]");
                Log.d("ASSIGN_LINKS", "Found (DownloadAssignment): " + assignLinks.size());

                if (assignLinks.isEmpty()) {
                    assignLinks = doc.select("a[href*=ssignment]");
                    Log.d("ASSIGN_LINKS", "Fallback found: " + assignLinks.size());
                }

                if (assignLinks.isEmpty()) {
                    runOnUiThread(() -> loadingText.setText("No assignments found."));
                    return;
                }

                java.util.List<String[]> subjects = new java.util.ArrayList<>();
                for (Element link : assignLinks) {
                    String subjectName = "";
                    Element row = link.closest("tr");
                    if (row != null) {
                        Elements tds = row.select("td");
                        if (!tds.isEmpty()) subjectName = tds.first().text().trim();
                    }
                    if (subjectName.isEmpty()) subjectName = link.text().trim();
                    if (subjectName.isEmpty()) subjectName = "Unknown Subject";

                    String href = link.attr("href");
                    String assignUrl = resolveUrl(href, "https://agclms.in/DashBoardStudent");
                    subjects.add(new String[]{subjectName, assignUrl});
                }

                subjects.sort((a, b) -> a[0].compareTo(b[0]));
                for (String[] subject : subjects) fetchAssignments(subject[0], subject[1]);

            } catch (Exception e) {
                Log.e("DASHBOARD_ERROR", e.toString());
                runOnUiThread(() -> loadingText.setText("Error: " + e.getMessage()));
            }
        }).start();
    }

    private void fetchAssignments(String subjectName, String assignUrl) {
        new Thread(() -> {
            try {
                java.net.HttpURLConnection conn = openConn(assignUrl, "https://agclms.in/DashBoardStudent");
                String html = readBody(conn);
                conn.disconnect();

                Document doc = Jsoup.parse(html);
                java.util.List<String[]> pdfs = new java.util.ArrayList<>();
                java.util.Set<String> seenUrls = new java.util.HashSet<>();

                Elements pdfLinks = doc.select("a[href*=.pdf]");
                Elements downloadLinks = doc.select("a[href*=Download], a[href*=download]");

                for (Element a : pdfLinks) {
                    String name = a.text().trim();
                    String href = a.attr("href");
                    String url = resolveUrl(href, assignUrl);
                    if (!name.isEmpty() && seenUrls.add(url)) pdfs.add(new String[]{name, url});
                }
                for (Element a : downloadLinks) {
                    String name = a.text().trim();
                    String href = a.attr("href");
                    String url = resolveUrl(href, assignUrl);
                    if (!name.isEmpty() && !url.contains("DashBoard") && !url.contains("Login")
                            && seenUrls.add(url)) {
                        pdfs.add(new String[]{name, url});
                    }
                }

                Log.d("PDF_LINKS", subjectName + " total PDFs found: " + pdfs.size());
                runOnUiThread(() -> addSubjectCard(subjectName, pdfs));

            } catch (Exception e) {
                Log.e("ASSIGN_ERROR", subjectName + ": " + e.toString());
                runOnUiThread(() -> addSubjectCard(subjectName, new java.util.ArrayList<>()));
            }
        }).start();
    }

    private void addSubjectCard(String subjectName, java.util.List<String[]> pdfs) {
        loadingText.setVisibility(View.GONE);
        scrollView.setVisibility(View.VISIBLE);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(0xFF1E1E1E);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cp.setMargins(0, 0, 0, 20);
        card.setLayoutParams(cp);
        card.setPadding(40, 36, 40, 36);

        TextView nameView = new TextView(this);
        nameView.setText(subjectName);
        nameView.setTextColor(0xFFFFFFFF);
        nameView.setTextSize(18);
        nameView.setTypeface(null, android.graphics.Typeface.BOLD);
        nameView.setPadding(0, 0, 0, 16);
        card.addView(nameView);

        if (pdfs.isEmpty()) {
            TextView noAssign = new TextView(this);
            noAssign.setText("No assignments uploaded");
            noAssign.setTextColor(0xFF888888);
            noAssign.setTextSize(14);
            card.addView(noAssign);
        } else {
            for (String[] pdf : pdfs) {
                String pdfName = pdf[0];
                String pdfUrl  = pdf[1];

                TextView pdfNameView = new TextView(this);
                pdfNameView.setText("📄 " + pdfName);
                pdfNameView.setTextColor(0xFFCCCCCC);
                pdfNameView.setTextSize(14);
                pdfNameView.setPadding(0, 0, 0, 10);
                card.addView(pdfNameView);

                LinearLayout btnRow = new LinearLayout(this);
                btnRow.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                rp.setMargins(0, 0, 0, 20);
                btnRow.setLayoutParams(rp);

                TextView viewBtn = new TextView(this);
                viewBtn.setText("  View  ");
                viewBtn.setTextColor(0xFFFFFFFF);
                viewBtn.setTextSize(14);
                viewBtn.setBackgroundColor(0xFF6200EE);
                viewBtn.setPadding(24, 16, 24, 16);
                LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                vp.setMargins(0, 0, 16, 0);
                viewBtn.setLayoutParams(vp);
                viewBtn.setOnClickListener(v -> viewPdf(pdfUrl, pdfName));
                btnRow.addView(viewBtn);

                TextView downloadBtn = new TextView(this);
                downloadBtn.setText("  Download  ");
                downloadBtn.setTextColor(0xFFFFFFFF);
                downloadBtn.setTextSize(14);
                downloadBtn.setBackgroundColor(0xFF018786);
                downloadBtn.setPadding(24, 16, 24, 16);
                downloadBtn.setOnClickListener(v -> downloadPdf(pdfUrl, pdfName));
                btnRow.addView(downloadBtn);

                card.addView(btnRow);
            }
        }
        container.addView(card);
    }

    // ── FIXED: streamPdfToOutput ──────────────────────────────────────────────────
    // Fixes: (1) lambda variable capture, (2) e.getMessage() lambda capture,
    //        (3) proper HTTP error handling, (4) HTML-instead-of-PDF detection,
    //        (5) cookie absorption on PDF requests
    private boolean streamPdfToOutput(String pdfUrl, OutputStream outputStream) {
        try {
            java.net.URL url = new java.net.URL(pdfUrl);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36");
            conn.setRequestProperty("Accept", "application/pdf,application/octet-stream,*/*;q=0.8");
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
            conn.setRequestProperty("Cookie", cookies);
            conn.setRequestProperty("Referer", "https://agclms.in/DashBoardStudent");
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.connect();

            absorbCookies(conn);

            int responseCode   = conn.getResponseCode();
            String contentType = conn.getContentType();
            String finalUrl    = conn.getURL().toString();

            Log.d("PDF_DEBUG", "=== PDF FETCH REPORT ===");
            Log.d("PDF_DEBUG", "URL      : " + pdfUrl);
            Log.d("PDF_DEBUG", "Final URL: " + finalUrl);
            Log.d("PDF_DEBUG", "HTTP     : " + responseCode);
            Log.d("PDF_DEBUG", "Type     : " + contentType);

            if (responseCode == 401 || responseCode == 403) {
                final String msg = "Access denied (HTTP " + responseCode + ") — session may have expired";
                runOnUiThread(() -> Toast.makeText(Assignment.this, msg, Toast.LENGTH_LONG).show());
                conn.disconnect();
                return false;
            }

            if (responseCode != 200) {
                final String msg = "HTTP error: " + responseCode;
                runOnUiThread(() -> Toast.makeText(Assignment.this, msg, Toast.LENGTH_LONG).show());
                conn.disconnect();
                return false;
            }

            // Server returned a login/error HTML page instead of the PDF
            if (contentType != null && contentType.toLowerCase().contains("text/html")) {
                java.io.BufferedReader br = new java.io.BufferedReader(
                        new java.io.InputStreamReader(conn.getInputStream()));
                StringBuilder snippet = new StringBuilder();
                String line;
                int chars = 0;
                while ((line = br.readLine()) != null && chars < 500) {
                    snippet.append(line);
                    chars += line.length();
                }
                br.close();
                conn.disconnect();
                Log.e("PDF_DEBUG", "Got HTML: " + snippet.toString().substring(0, Math.min(300, snippet.length())));
                runOnUiThread(() -> Toast.makeText(Assignment.this,
                        "Server returned HTML — session expired or file unavailable",
                        Toast.LENGTH_LONG).show());
                return false;
            }

            // Stream the PDF bytes
            InputStream is = conn.getInputStream();
            byte[] buffer = new byte[8192];
            int len;
            int totalBytes = 0;
            while ((len = is.read(buffer)) != -1) {
                outputStream.write(buffer, 0, len);
                totalBytes += len;
            }
            outputStream.flush();
            is.close();
            conn.disconnect();

            Log.d("PDF_DEBUG", "Bytes received: " + totalBytes);

            // FIX: capture into final variable before using in lambda
            final int bytesReceived = totalBytes;
            if (bytesReceived < 500) {
                runOnUiThread(() -> Toast.makeText(Assignment.this,
                        "File too small (" + bytesReceived + " bytes) — not a valid PDF",
                        Toast.LENGTH_LONG).show());
                return false;
            }

            Log.d("PDF_DEBUG", "PDF downloaded successfully: " + bytesReceived + " bytes");
            return true;

        } catch (Exception e) {
            Log.e("PDF_DEBUG", "Exception: " + e.toString());
            // FIX: capture message into final variable before using in lambda
            final String errMsg = e.getMessage() != null ? e.getMessage() : e.toString();
            runOnUiThread(() -> Toast.makeText(Assignment.this,
                    "Exception: " + errMsg, Toast.LENGTH_LONG).show());
            return false;
        }
    }

    // ── View PDF ──────────────────────────────────────────────────────────────────
    private void viewPdf(String pdfUrl, String pdfName) {
        Toast.makeText(Assignment.this, "Loading PDF…", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                String safeFileName = pdfName.replaceAll("[^a-zA-Z0-9._-]", "_");
                if (!safeFileName.toLowerCase().endsWith(".pdf")) safeFileName += ".pdf";

                File pdfFile = new File(getCacheDir(), safeFileName);
                // Always re-download so we get a fresh copy
                if (pdfFile.exists()) pdfFile.delete();

                FileOutputStream fos = new FileOutputStream(pdfFile);
                boolean success = streamPdfToOutput(pdfUrl, fos);
                fos.close();

                if (!success || pdfFile.length() == 0) {
                    if (pdfFile.exists()) pdfFile.delete();
                    return;
                }

                // FIX: Use Assignment.this explicitly — avoids ambiguous context in nested lambdas
                Uri fileUri = FileProvider.getUriForFile(
                        Assignment.this,
                        getPackageName() + ".provider",
                        pdfFile);

                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(fileUri, "application/pdf");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_ACTIVITY_NO_HISTORY
                        | Intent.FLAG_ACTIVITY_NEW_TASK);

                runOnUiThread(() -> {
                    try {
                        startActivity(Intent.createChooser(intent, "Open PDF with"));
                    } catch (android.content.ActivityNotFoundException ex) {
                        Toast.makeText(Assignment.this,
                                "No PDF viewer installed. Please install one from the Play Store.",
                                Toast.LENGTH_LONG).show();
                    }
                });

            } catch (Exception e) {
                Log.e("VIEW_PDF", e.toString());
                final String errMsg = e.getMessage() != null ? e.getMessage() : e.toString();
                runOnUiThread(() -> Toast.makeText(Assignment.this,
                        errMsg, Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    // ── Download PDF ──────────────────────────────────────────────────────────────
    private void downloadPdf(String pdfUrl, String pdfName) {
        Toast.makeText(Assignment.this, "Downloading…", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                String safeFileName = pdfName.replaceAll("[^a-zA-Z0-9._-]", "_");
                if (!safeFileName.toLowerCase().endsWith(".pdf")) safeFileName += ".pdf";

                boolean success;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Downloads.DISPLAY_NAME, safeFileName);
                    values.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
                    values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

                    Uri uri = getContentResolver().insert(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (uri == null) {
                        runOnUiThread(() -> Toast.makeText(Assignment.this,
                                "Could not create file in Downloads", Toast.LENGTH_SHORT).show());
                        return;
                    }
                    OutputStream os = getContentResolver().openOutputStream(uri);
                    success = streamPdfToOutput(pdfUrl, os);
                    os.close();
                    if (!success) getContentResolver().delete(uri, null, null);
                } else {
                    File dir = Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS);
                    if (!dir.exists()) dir.mkdirs();
                    File f = new File(dir, safeFileName);
                    FileOutputStream fos = new FileOutputStream(f);
                    success = streamPdfToOutput(pdfUrl, fos);
                    fos.close();
                    if (!success && f.exists()) f.delete();
                }

                if (success) {
                    final String name = safeFileName;
                    runOnUiThread(() -> Toast.makeText(Assignment.this,
                            "Saved to Downloads: " + name, Toast.LENGTH_LONG).show());
                }

            } catch (Exception e) {
                Log.e("DOWNLOAD_PDF", e.toString());
                final String errMsg = e.getMessage() != null ? e.getMessage() : e.toString();
                runOnUiThread(() -> Toast.makeText(Assignment.this,
                        errMsg, Toast.LENGTH_LONG).show());
            }
        }).start();
    }
}
