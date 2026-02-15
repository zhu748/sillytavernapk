package com.sillytavern.apk

import android.content.Context
import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class MainActivity : AppCompatActivity() {
    companion object {
        @Volatile
        private var nodeStarted = false

        init {
            System.loadLibrary("native-lib")
            System.loadLibrary("node")
        }
    }

    private external fun startNodeWithArguments(arguments: Array<String>): Int

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val webView = findViewById<WebView>(R.id.webview)
        configureWebView(webView)

        val appRoot = File(filesDir, "sillytavern")
        unpackAssetsIfNeeded(this, appRoot)
        startNodeIfNeeded(appRoot)

        waitForServerAndLoad(webView, "http://127.0.0.1:8000")
    }

    private fun configureWebView(webView: WebView) {
        webView.webViewClient = WebViewClient()
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true
    }

    private fun startNodeIfNeeded(appRoot: File) {
        if (nodeStarted) {
            return
        }
        synchronized(MainActivity::class.java) {
            if (nodeStarted) {
                return
            }
            val launcherFile = File(appRoot, "launcher.mjs")
            val args = arrayOf("node", launcherFile.absolutePath)
            Thread {
                startNodeWithArguments(args)
            }.start()
            nodeStarted = true
        }
    }

    private fun waitForServerAndLoad(webView: WebView, url: String) {
        Thread {
            var loaded = false
            repeat(120) {
                try {
                    val connection = java.net.URL(url).openConnection()
                    connection.connectTimeout = 500
                    connection.readTimeout = 500
                    connection.getInputStream().use { }
                    loaded = true
                    return@repeat
                } catch (_: Exception) {
                    Thread.sleep(500)
                }
            }
            runOnUiThread {
                webView.loadUrl(if (loaded) url else "about:blank")
            }
        }.start()
    }

    private fun unpackAssetsIfNeeded(context: Context, appRoot: File) {
        val preferences = context.getSharedPreferences("st_apk", Context.MODE_PRIVATE)
        val appVersionCode = packageManager.getPackageInfo(packageName, 0).longVersionCode
        val currentVersionCode = preferences.getLong("assets_version", -1L)

        if (appRoot.exists() && currentVersionCode == appVersionCode) {
            return
        }

        if (appRoot.exists()) {
            appRoot.deleteRecursively()
        }
        appRoot.mkdirs()

        copyAssetFolder("app", appRoot)

        preferences.edit().putLong("assets_version", appVersionCode).apply()
    }

    private fun copyAssetFolder(assetPath: String, destinationDir: File) {
        val assetManager = assets
        val entries = assetManager.list(assetPath) ?: return
        if (entries.isEmpty()) {
            assetManager.open(assetPath).use { input ->
                destinationDir.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            return
        }

        if (!destinationDir.exists()) {
            destinationDir.mkdirs()
        }

        for (asset in entries) {
            val childAssetPath = "$assetPath/$asset"
            val childDestination = File(destinationDir, asset)
            val childEntries = assetManager.list(childAssetPath)
            if (childEntries == null || childEntries.isEmpty()) {
                assetManager.open(childAssetPath).use { input ->
                    childDestination.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } else {
                copyAssetFolder(childAssetPath, childDestination)
            }
        }
    }
}
