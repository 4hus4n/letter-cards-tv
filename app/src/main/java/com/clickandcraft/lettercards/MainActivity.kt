package com.clickandcraft.lettercards

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private lateinit var web: WebView
    private var tts: TextToSpeech? = null
    @Volatile private var ttsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Serves the bundled app from app/src/main/assets over a secure local address
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web = WebView(this)
        web.setBackgroundColor(Color.parseColor("#FFE27A"))
        web.isFocusable = true
        web.isFocusableInTouchMode = true
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            userAgentString = "$userAgentString LetterCardsTV/${BuildConfig.VERSION_NAME}"
        }
        web.webChromeClient = WebChromeClient()
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                request.url.host != "appassets.androidplatform.net"
        }
        web.addJavascriptInterface(TtsBridge(), "AndroidTTS")
        setContentView(web)
        hideSystemUi()
        web.requestFocus(View.FOCUS_DOWN)
        web.loadUrl("https://appassets.androidplatform.net/assets/index.html")

        tts = TextToSpeech(this) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { ttsDone(utteranceId) }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { ttsDone(utteranceId) }
                override fun onError(utteranceId: String?, errorCode: Int) { ttsDone(utteranceId) }
            })
        }

        checkForUpdate()
    }

    private fun hideSystemUi() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    // ---------- Speech for Arabic (the TV's own text-to-speech) ----------
    private fun ttsDone(id: String?) {
        if (id == null) return
        val safe = id.replace("'", "")
        runOnUiThread { web.evaluateJavascript("window.__ttsDone&&__ttsDone('$safe')", null) }
    }

    inner class TtsBridge {
        @JavascriptInterface
        fun speak(text: String, lang: String, id: String) {
            val engine = tts
            if (engine == null || !ttsReady) { ttsDone(id); return }
            val result = engine.setLanguage(Locale.forLanguageTag(lang))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                ttsDone(id); return
            }
            engine.setSpeechRate(0.85f)
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), id)
        }

        @JavascriptInterface
        fun stop() { tts?.stop() }
    }

    // ---------- Remote: Back goes to the previous screen, exits from Home ----------
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            web.evaluateJavascript("(window.tvBack&&window.tvBack())?'1':'0'") { v ->
                if (v == null || !v.contains("1")) finish()
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    // ---------- Self-update from GitHub Releases ----------
    private fun checkForUpdate() {
        thread {
            try {
                val con = URL("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
                    .openConnection() as HttpURLConnection
                con.setRequestProperty("Accept", "application/vnd.github+json")
                con.connectTimeout = 8000
                con.readTimeout = 8000
                if (con.responseCode != 200) return@thread
                val json = JSONObject(con.inputStream.bufferedReader().use { it.readText() })
                val tag = json.optString("tag_name")               // e.g. v1.12
                val code = tag.substringAfterLast('.').toIntOrNull() ?: return@thread
                if (code <= BuildConfig.VERSION_CODE) return@thread
                val assets = json.getJSONArray("assets")
                var apkUrl: String? = null
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.getString("name").endsWith(".apk")) { apkUrl = a.getString("browser_download_url"); break }
                }
                val url = apkUrl ?: return@thread
                runOnUiThread {
                    if (isFinishing) return@runOnUiThread
                    AlertDialog.Builder(this)
                        .setTitle("Update available")
                        .setMessage("Version ${tag.removePrefix("v")} is ready. You have ${BuildConfig.VERSION_NAME}.")
                        .setPositiveButton("Update") { _, _ -> downloadUpdate(url) }
                        .setNegativeButton("Later", null)
                        .show()
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun downloadUpdate(url: String) {
        val dlg = AlertDialog.Builder(this)
            .setTitle("Downloading update…")
            .setMessage("Please wait")
            .setCancelable(false)
            .show()
        thread {
            try {
                val file = File(cacheDir, "update.apk")
                fun open(u: String): HttpURLConnection =
                    (URL(u).openConnection() as HttpURLConnection).apply {
                        instanceFollowRedirects = false
                        connectTimeout = 15000
                        readTimeout = 30000
                    }
                var con = open(url)
                var hops = 0
                while (con.responseCode in 300..399 && hops < 5) {
                    val next = con.getHeaderField("Location") ?: break
                    con.disconnect()
                    con = open(next)
                    hops++
                }
                val total = con.contentLength
                con.inputStream.use { input ->
                    file.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        var last = -1
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (total > 0) {
                                val pct = (done * 100 / total).toInt()
                                if (pct != last) { last = pct; runOnUiThread { dlg.setMessage("$pct%") } }
                            }
                        }
                    }
                }
                runOnUiThread { dlg.dismiss(); installApk(file) }
            } catch (e: Exception) {
                runOnUiThread {
                    dlg.dismiss()
                    Toast.makeText(this, "Download failed. Check the internet and try again.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun installApk(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            Toast.makeText(this, "Allow this app to install updates, then press Back.", Toast.LENGTH_LONG).show()
            getSharedPreferences("upd", MODE_PRIVATE).edit().putBoolean("waiting", true).apply()
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            return
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
        // After allowing installs in Settings, the cached update can be installed straight away
        val f = File(cacheDir, "update.apk")
        if (f.exists() && (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || packageManager.canRequestPackageInstalls())) {
            val pending = getSharedPreferences("upd", MODE_PRIVATE)
            if (pending.getBoolean("waiting", false)) {
                pending.edit().putBoolean("waiting", false).apply()
                installApk(f)
            }
        }
    }

    override fun onPause() {
        web.onPause()
        tts?.stop()
        super.onPause()
    }

    override fun onDestroy() {
        tts?.shutdown()
        web.destroy()
        super.onDestroy()
    }
}
