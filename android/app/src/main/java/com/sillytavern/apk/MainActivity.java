package com.sillytavern.apk;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

public class MainActivity extends AppCompatActivity {
    private static volatile boolean nodeStarted = false;

    static {
        System.loadLibrary("native-lib");
        System.loadLibrary("node");
    }

    public native int startNodeWithArguments(String[] arguments);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        WebView webView = findViewById(R.id.webview);
        configureWebView(webView);

        File appRoot = new File(getFilesDir(), "sillytavern");
        unpackAssetsIfNeeded(this, appRoot);
        startNodeIfNeeded(appRoot);

        waitForServerAndLoad(webView, "http://127.0.0.1:8000");
    }

    private void configureWebView(WebView webView) {
        webView.setWebViewClient(new WebViewClient());
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
    }

    private void startNodeIfNeeded(File appRoot) {
        if (nodeStarted) {
            return;
        }

        synchronized (MainActivity.class) {
            if (nodeStarted) {
                return;
            }

            File launcherFile = new File(appRoot, "launcher.mjs");
            String[] args = new String[]{"node", launcherFile.getAbsolutePath()};
            new Thread(() -> startNodeWithArguments(args)).start();
            nodeStarted = true;
        }
    }

    private void waitForServerAndLoad(WebView webView, String url) {
        new Thread(() -> {
            boolean loaded = false;
            for (int i = 0; i < 120; i++) {
                try {
                    URLConnection connection = new URL(url).openConnection();
                    connection.setConnectTimeout(500);
                    connection.setReadTimeout(500);
                    connection.getInputStream().close();
                    loaded = true;
                    break;
                } catch (Exception ignored) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            final boolean finalLoaded = loaded;
            runOnUiThread(() -> webView.loadUrl(finalLoaded ? url : "about:blank"));
        }).start();
    }

    private void unpackAssetsIfNeeded(Context context, File appRoot) {
        SharedPreferences preferences = context.getSharedPreferences("st_apk", Context.MODE_PRIVATE);
        long appVersionCode;
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            appVersionCode = packageInfo.getLongVersionCode();
        } catch (Exception e) {
            appVersionCode = 1L;
        }

        long currentVersionCode = preferences.getLong("assets_version", -1L);
        if (appRoot.exists() && currentVersionCode == appVersionCode) {
            return;
        }

        if (appRoot.exists()) {
            //noinspection ResultOfMethodCallIgnored
            appRoot.deleteRecursively();
        }
        //noinspection ResultOfMethodCallIgnored
        appRoot.mkdirs();

        copyAssetFolder("app", appRoot);
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
}
