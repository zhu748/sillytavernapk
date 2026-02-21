package com.sillytavern.apk;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.net.URL;
import java.net.URLConnection;

public class MainActivity extends AppCompatActivity {
    private static volatile boolean nodeStarted = false;

    private WebView webView;
    private ProgressBar progressBar;
    private TextView statusText;

    private File appRoot;
    private File settingsFile;

    static {
        System.loadLibrary("native-lib");
        System.loadLibrary("node");
    }

    public native int startNodeWithArguments(String[] arguments);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        progressBar = findViewById(R.id.progress);
        statusText = findViewById(R.id.status_text);

        configureWebView(webView);

        appRoot = new File(getFilesDir(), "sillytavern");
        settingsFile = new File(getFilesDir(), "st-settings.json");

        unpackAssetsIfNeeded(this, appRoot);
        ensureSettingsFile(settingsFile);

        JSONObject settingsJson = readSettingsJson(settingsFile);
        int port = getPort(settingsJson);

        bindUiActions(port);
        startNodeIfNeeded(appRoot, settingsFile);
        waitForServerAndLoad(webView, "http://127.0.0.1:" + port);
    }

    private void bindUiActions(int port) {
        findViewById(R.id.btn_settings).setOnClickListener(v -> openSettingsDialog());
        findViewById(R.id.btn_reload).setOnClickListener(v -> webView.reload());

        statusText.setText("Starting SillyTavern on 127.0.0.1:" + port + " ...");
    }

    private void configureWebView(WebView targetWebView) {
        targetWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);
                statusText.setText("Running");
            }
        });

        WebSettings settings = targetWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
    }

    private void startNodeIfNeeded(File nodeAppRoot, File nodeSettingsFile) {
        if (nodeStarted) {
            return;
        }

        synchronized (MainActivity.class) {
            if (nodeStarted) {
                return;
            }

            File launcherFile = new File(nodeAppRoot, "launcher.mjs");
            String[] args = new String[]{
                "node",
                launcherFile.getAbsolutePath(),
                nodeSettingsFile.getAbsolutePath()
            };
            new Thread(() -> startNodeWithArguments(args)).start();
            nodeStarted = true;
        }
    }

    private void waitForServerAndLoad(WebView targetWebView, String url) {
        new Thread(() -> {
            boolean loaded = false;
            for (int i = 0; i < 120; i++) {
                final int tick = i;
                runOnUiThread(() -> statusText.setText("Waiting for local server... " + tick + "s"));
                try {
                    URLConnection connection = new URL(url).openConnection();
                    connection.setConnectTimeout(500);
                    connection.setReadTimeout(500);
                    connection.getInputStream().close();
                    loaded = true;
                    break;
                } catch (Exception ignored) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            final boolean finalLoaded = loaded;
            runOnUiThread(() -> {
                if (finalLoaded) {
                    targetWebView.loadUrl(url);
                } else {
                    progressBar.setVisibility(View.GONE);
                    statusText.setText("Startup failed. Check settings and restart app.");
                    Toast.makeText(this, "SillyTavern failed to start", Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private void openSettingsDialog() {
        JSONObject settingsJson = readSettingsJson(settingsFile);
        String envJson = settingsJson.optJSONObject("env") != null
            ? settingsJson.optJSONObject("env").toString()
            : "{}";
        String configJson = settingsJson.optJSONObject("config") != null
            ? settingsJson.optJSONObject("config").toString()
            : "{}";

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = dpToPx(16);
        container.setPadding(padding, padding, padding, padding);

        TextView envTitle = new TextView(this);
        envTitle.setText("Environment Variables JSON (env)");
        container.addView(envTitle);

        EditText envInput = new EditText(this);
        envInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        envInput.setMinLines(8);
        envInput.setText(envJson);
        container.addView(envInput);

        TextView configTitle = new TextView(this);
        configTitle.setText("Config JSON (config)\nExample: {\"port\":8000,\"listen\":false}");
        configTitle.setPadding(0, dpToPx(12), 0, 0);
        container.addView(configTitle);

        EditText configInput = new EditText(this);
        configInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        configInput.setMinLines(8);
        configInput.setText(configJson);
        container.addView(configInput);

        new AlertDialog.Builder(this)
            .setTitle("Runtime Settings")
            .setView(container)
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Save", (dialog, which) -> {
                try {
                    JSONObject envObj = new JSONObject(envInput.getText().toString().trim().isEmpty() ? "{}" : envInput.getText().toString());
                    JSONObject configObj = new JSONObject(configInput.getText().toString().trim().isEmpty() ? "{}" : configInput.getText().toString());

                    JSONObject merged = new JSONObject();
                    merged.put("env", envObj);
                    merged.put("config", configObj);

                    writeJsonFile(settingsFile, merged);
                    Toast.makeText(this, "Saved. Restart app to apply.", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(this, "Invalid JSON: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            })
            .setPositiveButton("Save & Restart", (dialog, which) -> {
                try {
                    JSONObject envObj = new JSONObject(envInput.getText().toString().trim().isEmpty() ? "{}" : envInput.getText().toString());
                    JSONObject configObj = new JSONObject(configInput.getText().toString().trim().isEmpty() ? "{}" : configInput.getText().toString());

                    JSONObject merged = new JSONObject();
                    merged.put("env", envObj);
                    merged.put("config", configObj);

                    writeJsonFile(settingsFile, merged);
                    restartApp();
                } catch (Exception e) {
                    Toast.makeText(this, "Invalid JSON: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            })
            .show();
    }

    private void restartApp() {
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(launchIntent);
            Runtime.getRuntime().exit(0);
        }
    }

    private void ensureSettingsFile(File file) {
        if (file.exists()) {
            return;
        }

        try {
            JSONObject defaults = new JSONObject();
            JSONObject env = new JSONObject();
            JSONObject config = new JSONObject();
            config.put("port", 8000);
            config.put("listen", false);
            config.put("browserLaunchEnabled", false);

            defaults.put("env", env);
            defaults.put("config", config);

            writeJsonFile(file, defaults);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize settings file", e);
        }
    }

    private JSONObject readSettingsJson(File file) {
        try {
            if (!file.exists()) {
                return new JSONObject("{\"env\":{},\"config\":{\"port\":8000,\"listen\":false}}\n");
            }
            String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            JSONObject parsed = new JSONObject(content);
            if (!parsed.has("env")) {
                parsed.put("env", new JSONObject());
            }
            if (!parsed.has("config")) {
                parsed.put("config", new JSONObject());
            }
            return parsed;
        } catch (Exception e) {
            Toast.makeText(this, "Settings parse failed, fallback to defaults", Toast.LENGTH_SHORT).show();
            try {
                return new JSONObject("{\"env\":{},\"config\":{\"port\":8000,\"listen\":false}}\n");
            } catch (JSONException jsonException) {
                throw new RuntimeException(jsonException);
            }
        }
    }

    private int getPort(JSONObject settingsJson) {
        JSONObject config = settingsJson.optJSONObject("config");
        int port = 8000;
        if (config != null) {
            port = config.optInt("port", 8000);
        }
        if (port < 1 || port > 65535) {
            return 8000;
        }
        return port;
    }

    private void writeJsonFile(File file, JSONObject jsonObject) throws Exception {
        if (file.getParentFile() != null && !file.getParentFile().exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.getParentFile().mkdirs();
        }
        Files.write(file.toPath(), jsonObject.toString(2).getBytes(StandardCharsets.UTF_8));
    }

    private void unpackAssetsIfNeeded(Context context, File nodeAppRoot) {
        SharedPreferences preferences = context.getSharedPreferences("st_apk", Context.MODE_PRIVATE);
        long appVersionCode;
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            appVersionCode = packageInfo.getLongVersionCode();
        } catch (Exception e) {
            appVersionCode = 1L;
        }

        long currentVersionCode = preferences.getLong("assets_version", -1L);
        if (nodeAppRoot.exists() && currentVersionCode == appVersionCode) {
            return;
        }

        if (nodeAppRoot.exists()) {
            deleteRecursively(nodeAppRoot);
        }
        //noinspection ResultOfMethodCallIgnored
        nodeAppRoot.mkdirs();

        copyAssetFolder("app", nodeAppRoot);
        preferences.edit().putLong("assets_version", appVersionCode).apply();
    }

    private void copyAssetFolder(String assetPath, File destinationDir) {
        try {
            String[] entries = getAssets().list(assetPath);
            if (entries == null) {
                return;
            }

            if (entries.length == 0) {
                InputStream input = getAssets().open(assetPath);
                FileOutputStream output = new FileOutputStream(destinationDir);
                copyStream(input, output);
                input.close();
                output.close();
                return;
            }

            if (!destinationDir.exists()) {
                //noinspection ResultOfMethodCallIgnored
                destinationDir.mkdirs();
            }

            for (String entry : entries) {
                String childAssetPath = assetPath + "/" + entry;
                File childDestination = new File(destinationDir, entry);
                String[] childEntries = getAssets().list(childAssetPath);
                if (childEntries == null || childEntries.length == 0) {
                    InputStream input = getAssets().open(childAssetPath);
                    FileOutputStream output = new FileOutputStream(childDestination);
                    copyStream(input, output);
                    input.close();
                    output.close();
                } else {
                    copyAssetFolder(childAssetPath, childDestination);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to copy assets", e);
        }
    }

    private void copyStream(InputStream input, FileOutputStream output) throws Exception {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
    }

    private void deleteRecursively(File target) {
        if (target == null || !target.exists()) {
            return;
        }

        if (target.isDirectory()) {
            File[] children = target.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }

        //noinspection ResultOfMethodCallIgnored
        target.delete();
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (dp * density);
    }
}
