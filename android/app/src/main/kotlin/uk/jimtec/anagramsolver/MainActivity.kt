package uk.jimtec.anagramsolver

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.io.ByteArrayInputStream

/**
 * A thin shell around the website in the repo root. The web files are copied into
 * assets/site at build time and served from a private https origin, so the page
 * runs exactly as it does online, offline and without the INTERNET permission.
 *
 * The shell adds three things to index.html as it serves it: the "android" class
 * (style.css moves the controls to the bottom), the system's Material You colours
 * on Android 12+, and the system bar insets.
 */
class MainActivity : ComponentActivity() {
    private lateinit var web: WebView
    private val prefs by lazy { getSharedPreferences("shell", MODE_PRIVATE) }

    /** "system", "light" or "dark", as last reported by theme.js. */
    @Volatile private var themeChoice = "system"

    /** Latest inset CSS variables; also read on the loader thread when index.html is served. */
    @Volatile private var insetVars = ""

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        themeChoice = prefs.getString(KEY_THEME, "system") ?: "system"
        applySystemBars()
        super.onCreate(savedInstanceState)

        web = WebView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            webViewClient = ShellClient()
        }
        val root = FrameLayout(this).apply { addView(web) }
        setContentView(root)

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            // The keyboard shrinks the WebView; the bars are padded inside the page so its
            // background runs behind them.
            view.updatePadding(bottom = ime.bottom)
            val bottom = if (ime.bottom > 0) 0 else bars.bottom
            setInsets(bars.top, bars.right, bottom, bars.left)
            WindowInsetsCompat.CONSUMED
        }

        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(web, "AnagramApp", setOf("https://$HOST")) { _, msg, _, _, _ ->
                val choice = msg.data ?: return@addWebMessageListener
                if (choice in setOf("system", "light", "dark") && choice != themeChoice) {
                    themeChoice = choice
                    prefs.edit(commit = true) { putString(KEY_THEME, choice) }
                    applySystemBars()
                }
            }
        }

        if (savedInstanceState == null || web.restoreState(savedInstanceState) == null) {
            web.loadUrl(startUrl(intent))
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasExtra(Intent.EXTRA_PROCESS_TEXT)) web.loadUrl(startUrl(intent))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applySystemBars()
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }

    private fun startUrl(intent: Intent?): String {
        val text = intent?.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            ?.filter { it.isLetter() || it == '?' || it == ' ' }?.take(20)?.trim()
        return if (text.isNullOrEmpty()) "https://$HOST/" else "https://$HOST/?q=" + Uri.encode(text)
    }

    private fun isDark(): Boolean = when (themeChoice) {
        "dark" -> true
        "light" -> false
        else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }

    /** Transparent bars with icons that contrast with the page in the theme in use. */
    private fun applySystemBars() {
        val dark = isDark()
        val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
        enableEdgeToEdge(style, style)
    }

    private fun setInsets(top: Int, right: Int, bottom: Int, left: Int) {
        val d = resources.displayMetrics.density
        fun css(v: Int) = "${(v / d).toInt()}px"
        val vars = "--inset-top:${css(top)};--inset-right:${css(right)};" +
            "--inset-bottom:${css(bottom)};--inset-left:${css(left)}"
        if (vars == insetVars) return
        insetVars = vars
        pushInsets()
    }

    private fun pushInsets() {
        if (!::web.isInitialized) return
        val js = insetVars.split(';').filter { it.isNotEmpty() }.joinToString("") { p ->
            val (k, v) = p.split(':')
            "document.documentElement.style.setProperty('$k','$v');"
        }
        web.evaluateJavascript(js, null)
    }

    /** Material You colours for the page's CSS variables, or nothing before Android 12. */
    private fun dynamicColourCss(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return ""
        fun c(id: Int) = String.format("#%06X", getColor(id) and 0xFFFFFF)
        val light = listOf(
            "bg" to android.R.color.system_neutral1_50,
            "card" to android.R.color.system_neutral1_10,
            "text" to android.R.color.system_neutral1_900,
            "muted" to android.R.color.system_neutral2_700,
            "accent" to android.R.color.system_accent1_600,
            "accent-soft" to android.R.color.system_accent2_100,
            "tile" to android.R.color.system_accent1_100,
            "tile-edge" to android.R.color.system_accent1_300,
            "border" to android.R.color.system_neutral2_200,
        ).joinToString("") { (k, v) -> "--$k:${c(v)};" }
        val dark = listOf(
            "bg" to android.R.color.system_neutral1_900,
            "card" to android.R.color.system_neutral1_800,
            "text" to android.R.color.system_neutral1_100,
            "muted" to android.R.color.system_neutral2_200,
            "accent" to android.R.color.system_accent1_200,
            "accent-soft" to android.R.color.system_accent2_700,
            "tile" to android.R.color.system_accent1_700,
            "tile-edge" to android.R.color.system_accent1_500,
            "border" to android.R.color.system_neutral2_700,
        ).joinToString("") { (k, v) -> "--$k:${c(v)};" }
        // Same selectors as style.css; this sheet comes later, so it wins.
        return ":root{$light}" +
            "@media (prefers-color-scheme: dark){:root:not([data-theme=\"light\"]){$dark}}" +
            ":root[data-theme=\"dark\"]{$dark}"
    }

    private fun indexHtml(): WebResourceResponse {
        val html = assets.open("site/index.html").bufferedReader().use { it.readText() }
            .replaceFirst("<html lang=\"en-GB\">", "<html lang=\"en-GB\" class=\"android\">")
            // WebView writes localStorage lazily, so a theme picked just before the app was
            // killed can be lost there. The shell's saved copy wins, before theme.js reads it.
            .replaceFirst(
                "<meta charset=\"utf-8\">",
                "<meta charset=\"utf-8\">\n  <script>try{localStorage.setItem('theme','$themeChoice')}catch(e){}</script>",
            )
            .replaceFirst(
                "</head>",
                "<style id=\"android-shell\">:root{$insetVars}${dynamicColourCss()}</style>\n</head>",
            )
        return WebResourceResponse("text/html", "utf-8", ByteArrayInputStream(html.toByteArray()))
    }

    private inner class ShellClient : WebViewClient() {
        private val assetsHandler = WebViewAssetLoader.AssetsPathHandler(this@MainActivity)
        private val loader = WebViewAssetLoader.Builder()
            .setDomain(HOST)
            .addPathHandler("/") { path ->
                when (path) {
                    "", "index.html" -> indexHtml()
                    else -> assetsHandler.handle("site/$path")
                }
            }
            .build()

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
            // Anything that isn't one of our files gets an empty 404, never the network.
            loader.shouldInterceptRequest(request.url)
                ?: WebResourceResponse("text/plain", "utf-8", 404, "Not Found", null, null)

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (request.url.host == HOST) return false
            try {
                startActivity(Intent(Intent.ACTION_VIEW, request.url))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this@MainActivity, R.string.no_browser, Toast.LENGTH_SHORT).show()
            }
            return true
        }

        // Insets can change while the page loads, after index.html was served.
        override fun onPageFinished(view: WebView, url: String) = pushInsets()

        // The WebView's renderer was killed (usually for memory): start again rather than crash.
        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            recreate()
            return true
        }
    }

    private companion object {
        const val HOST = "appassets.androidplatform.net"
        const val KEY_THEME = "theme"
    }
}
