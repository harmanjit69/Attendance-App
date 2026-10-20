package com.example.attandenceapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;

public class MainActivity extends AppCompatActivity {
    private RequestQueue requestQueue;
    private String token = "";
    private EditText rollnoInput, passwordInput;
    private Button loginbutton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ThemeManager.applyTheme(this);

        requestQueue = Volley.newRequestQueue(this);

        rollnoInput = findViewById(R.id.editTextText2);
        passwordInput = findViewById(R.id.editTextTextPassword);
        loginbutton = findViewById(R.id.loginbutton);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        loginbutton.setOnClickListener(v -> {
            String rollNo = rollnoInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();
            if (rollNo.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Roll No or Password field cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }
            fetchTokenAndLogin(rollNo, password);
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.menu_timetable) {
            Toast.makeText(this, "TimeTable", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(MainActivity.this, TimeTable.class);
            startActivity(intent);
            return true;
        }

        if (id == R.id.menu_about) {
            Toast.makeText(this, "About", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(MainActivity.this, AboutMe.class);
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void fetchTokenAndLogin(String rollNo, String password) {
        new Thread(() -> {
            try {
                // Use a single CookieManager for the ENTIRE login flow
                java.net.CookieManager cookieManager = new java.net.CookieManager(
                        null, java.net.CookiePolicy.ACCEPT_ALL);
                java.net.CookieHandler.setDefault(cookieManager);

                // ── STEP 1: GET login page to collect initial cookies + CSRF token ──
                java.net.URL getUrl = new java.net.URL("https://agclms.in/Elogin/StudentLogin");
                java.net.HttpURLConnection getConn = (java.net.HttpURLConnection) getUrl.openConnection();
                getConn.setRequestMethod("GET");
                getConn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36");
                getConn.setInstanceFollowRedirects(true);
                getConn.connect();

                java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(getConn.getInputStream()));
                StringBuilder html = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) html.append(line);
                reader.close();
                getConn.disconnect();

                // Parse CSRF token
                org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(html.toString());
                org.jsoup.nodes.Element tokenEl = doc.select("input[name=__RequestVerificationToken]").first();
                if (tokenEl == null) {
                    Log.e("TOKEN", "Token not found!");
                    runOnUiThread(() -> Toast.makeText(this, "Unable to connect. Please try again.", Toast.LENGTH_SHORT).show());
                    return;
                }
                token = tokenEl.attr("value");
                Log.d("TOKEN", "Got token: " + token);

                // Build cookie string from CookieManager (captured automatically via CookieHandler)
                String cookiesForPost = buildCookieString(cookieManager);
                Log.d("COOKIES_FOR_POST", cookiesForPost);

                // ── STEP 2: POST login credentials ──
                String postData = "StudentId=" + java.net.URLEncoder.encode(rollNo, "UTF-8")
                        + "&Password=" + java.net.URLEncoder.encode(password, "UTF-8")
                        + "&__RequestVerificationToken=" + java.net.URLEncoder.encode(token, "UTF-8");

                java.net.URL postUrl = new java.net.URL("https://agclms.in/Elogin/StudentLogin");
                java.net.HttpURLConnection postConn = (java.net.HttpURLConnection) postUrl.openConnection();
                postConn.setRequestMethod("POST");
                postConn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36");
                postConn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                postConn.setRequestProperty("Referer", "https://agclms.in/Elogin/StudentLogin");
                postConn.setRequestProperty("Origin", "https://agclms.in");
                postConn.setRequestProperty("Cookie", cookiesForPost);
                postConn.setInstanceFollowRedirects(true);
                postConn.setDoOutput(true);

                java.io.OutputStream os = postConn.getOutputStream();
                os.write(postData.getBytes("UTF-8"));
                os.flush();
                os.close();

                int responseCode = postConn.getResponseCode();
                Log.d("LOGIN_STATUS", "POST response code: " + responseCode);

                // Read POST response body
                java.io.InputStream inputStream = postConn.getInputStream();
                java.io.BufferedReader postReader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(inputStream));
                StringBuilder postHtml = new StringBuilder();
                String postLine;
                while ((postLine = postReader.readLine()) != null) postHtml.append(postLine);
                postReader.close();

                // Collect NEW cookies set by the POST response (the auth session cookie)
                // Merge manually since CookieManager may not capture POST response cookies
                java.util.Map<String, String> allCookies = parseCookieString(cookiesForPost);
                java.util.List<String> postSetCookies = postConn.getHeaderFields().get("Set-Cookie");
                if (postSetCookies != null) {
                    for (String sc : postSetCookies) {
                        String pair = sc.split(";")[0].trim();
                        int eq = pair.indexOf('=');
                        if (eq > 0) {
                            allCookies.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
                        }
                    }
                }
                // Also grab anything the CookieManager captured from POST
                for (java.net.HttpCookie c : cookieManager.getCookieStore().getCookies()) {
                    allCookies.put(c.getName(), c.getValue());
                }

                String finalCookies = cookieMapToString(allCookies);
                Log.d("FINAL_COOKIES", finalCookies);

                // Wrong credentials check — if login page is returned again, creds are wrong
                org.jsoup.nodes.Document postDoc = org.jsoup.Jsoup.parse(postHtml.toString());
                org.jsoup.nodes.Element loginFormToken = postDoc.select("input[name=__RequestVerificationToken]").first();
                if (loginFormToken != null) {
                    Log.d("LOGIN_STATUS", "Wrong credentials");
                    runOnUiThread(() -> Toast.makeText(this, "Incorrect Roll No or Password", Toast.LENGTH_SHORT).show());
                    return;
                }

                // ── STEP 3: Verify session works by hitting the dashboard ──
                // This confirms the cookie is valid before we pass it forward
                java.net.URL dashUrl = new java.net.URL("https://agclms.in/DashBoardStudent");
                java.net.HttpURLConnection dashConn = (java.net.HttpURLConnection) dashUrl.openConnection();
                dashConn.setRequestMethod("GET");
                dashConn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36");
                dashConn.setRequestProperty("Cookie", finalCookies);
                dashConn.setRequestProperty("Referer", "https://agclms.in/Elogin/StudentLogin");
                dashConn.setInstanceFollowRedirects(true);
                dashConn.connect();

                int dashCode = dashConn.getResponseCode();
                Log.d("DASHBOARD_CHECK", "Dashboard response: " + dashCode);

                // Grab any new cookies set by dashboard response too
                java.util.List<String> dashSetCookies = dashConn.getHeaderFields().get("Set-Cookie");
                if (dashSetCookies != null) {
                    for (String sc : dashSetCookies) {
                        String pair = sc.split(";")[0].trim();
                        int eq = pair.indexOf('=');
                        if (eq > 0) allCookies.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
                    }
                }
                dashConn.disconnect();

                String verifiedCookies = cookieMapToString(allCookies);
                Log.d("VERIFIED_COOKIES", verifiedCookies);

                runOnUiThread(() -> {
                    SharedPreferences loginPrefs = getSharedPreferences("login_data", MODE_PRIVATE);
                    loginPrefs.edit().putString("roll", rollNo).putString("pass", password).apply();
                    Intent intent = new Intent(this, Selection.class);
                    intent.putExtra("cookies", verifiedCookies);
                    startActivity(intent);
                });

            } catch (Exception e) {
                Log.e("LOGIN_EXCEPTION", e.toString());
                runOnUiThread(() -> Toast.makeText(this, "Network error. Check your connection.", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // ── Cookie helpers ────────────────────────────────────────────────────────────

    private String buildCookieString(java.net.CookieManager cookieManager) {
        StringBuilder sb = new StringBuilder();
        for (java.net.HttpCookie c : cookieManager.getCookieStore().getCookies()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(c.getName()).append("=").append(c.getValue());
        }
        return sb.toString();
    }

    private java.util.Map<String, String> parseCookieString(String cookieStr) {
        java.util.Map<String, String> map = new java.util.LinkedHashMap<>();
        if (cookieStr == null || cookieStr.isEmpty()) return map;
        for (String part : cookieStr.split(";")) {
            part = part.trim();
            int eq = part.indexOf('=');
            if (eq > 0) map.put(part.substring(0, eq).trim(), part.substring(eq + 1).trim());
        }
        return map;
    }

    private String cookieMapToString(java.util.Map<String, String> map) {
        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<String, String> e : map.entrySet()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(e.getKey()).append("=").append(e.getValue());
        }
        return sb.toString();
    }
}
