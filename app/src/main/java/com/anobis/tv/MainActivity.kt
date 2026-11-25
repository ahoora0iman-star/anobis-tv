package com.anobis.tv

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.*
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import org.json.JSONArray
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp

class MainActivity : Activity() {

    companion object {
        const val SITE_URL = "https://www.anobis.site/"
        const val SKIN_REDIRECT_URL =
            "https://www.anobis.site/index.php?action_skin_change=yes&skin_name=smart"
        const val TV_UA =
            "Mozilla/5.0 (SMART-TV; Linux; Tizen 6.0) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/4.0 Chrome/76.0.3809.146 TV Safari/537.36"
        const val SEEK_SECONDS = 5

        // اگر لوگو را با پس‌زمینهٔ شفاف آپلود کردید، false کنید
        const val LOGO_HAS_WHITE_BG = true



        private const val PREFS = "anobis"
        private const val KEY_SKIN_OK = "skin_cookie_ok"

        private val TV_JS = """
(function(){
  if (window.__tv) return; window.__tv = 1;

  var userMoved = false;
  window.addEventListener('keydown', function(){ userMoved = true; }, true);

  function bridge(name, arg){
    try { if (window.AnobisBridge && window.AnobisBridge[name]) window.AnobisBridge[name](arg || ''); }
    catch (e) {}
  }
  function busyOn(){ bridge('loading'); }
  function busyOff(){ bridge('ready'); }

  var st = document.createElement('style');
  st.textContent = ':focus{outline:6px solid #FFD580 !important;outline-offset:4px;border-radius:6px;box-shadow:0 0 0 2px rgba(255,213,128,0.35) !important}';
  (document.head || document.documentElement).appendChild(st);

  function area(e){ var r = e.getBoundingClientRect(); return r.width * r.height; }
  function rootOf(el){
    var r = null, p = el;
    while (p && p.getAttribute) { if (p.hasAttribute('data-tvplayer')) r = p; p = p.parentElement; }
    return r;
  }

  function markPlayer(el){
    var a = area(el); if (a < 1) return;
    var root = el, p = el.parentElement;
    while (p && p !== document.body && area(p) <= a * 1.6) { root = p; p = p.parentElement; }
    if (root === el && el.tagName === 'IFRAME') root = el.parentElement;
    if (!root) return;
    root.setAttribute('data-tvplayer', '1');
    if (root.tabIndex < 0) root.setAttribute('tabindex', '0');
  }
  function mark(){
    [].forEach.call(document.querySelectorAll('video'), function(v){
      if (!(v.loop && v.muted)) markPlayer(v);
    });
    [].forEach.call(document.querySelectorAll('iframe'), function(f){
      var r = f.getBoundingClientRect();
      if (r.width > 300 && r.height > 150) markPlayer(f);
    });
    [].forEach.call(document.querySelectorAll('[onclick],[role="button"]'), function(e){
      if (!e.hasAttribute('tabindex')) e.setAttribute('tabindex', '0');
    });
  }
  var mt = null;
  mark();
  new MutationObserver(function(){ clearTimeout(mt); mt = setTimeout(mark, 300); })
    .observe(document.documentElement, {childList: true, subtree: true});
  setInterval(mark, 2000);

  var SEL = 'a[href],button,input,select,textarea,[data-tvplayer],[onclick],[role="button"],[tabindex]:not([tabindex="-1"])';
  function visible(e){
    var r = e.getBoundingClientRect();
    if (r.width < 2 || r.height < 2) return false;
    var s = getComputedStyle(e);
    return s.visibility !== 'hidden' && s.display !== 'none' && s.opacity !== '0' && !e.disabled;
  }
  function items(){
    return [].filter.call(document.querySelectorAll(SEL), function(e){
      if (!visible(e)) return false;
      var r = rootOf(e); return !r || r === e;
    });
  }

  // فوکوس خودکار روی دکمهٔ «پخش آنلاین»
  function norm(s){
    return (s || '').replace(/ي/g, 'ی').replace(/‌/g, ' ').replace(/\s+/g, ' ').trim();
  }
  function findPlayBtn(){
    var all = document.querySelectorAll('a,button,[onclick],[role="button"],[tabindex],span,div,li');
    var best = null;
    for (var i = 0; i < all.length; i++) {
      var e = all[i], t = norm(e.textContent);
      if (t.length > 40 || !/پخش ?آنلاین/.test(t)) continue;
      if (!visible(e)) continue;
      if (!best || best.contains(e)) best = e;
    }
    if (!best) return null;
    var c = best.closest('a,button,[onclick],[role="button"],[tabindex]:not([tabindex="-1"])');
    if (c && norm(c.textContent).length <= 60) best = c;
    if (best.tabIndex < 0) best.setAttribute('tabindex', '0');
    return best;
  }
  var tries = 0;
  (function autoFocus(){
    if (userMoved) return;
    var b = findPlayBtn();
    if (b) { b.focus(); b.scrollIntoView({block: 'center'}); return; }
    if (++tries < 12) setTimeout(autoFocus, 500);
  })();

  function isFs(){ return document.fullscreenElement || document.webkitFullscreenElement; }
  function fsVideo(){
    var f = isFs(); if (!f) return null;
    return f.tagName === 'VIDEO' ? f : f.querySelector('video');
  }
  var lastVideo = null, lastRoot = null;
  function pick(){
    var v = fsVideo(); if (v) return v;
    if (lastVideo && document.contains(lastVideo)) return lastVideo;
    var vs = [].slice.call(document.querySelectorAll('video'));
    return vs.filter(function(x){ return !x.paused; })[0] || vs[0] || null;
  }
  function goFull(el){
    var f = el.requestFullscreen || el.webkitRequestFullscreen || el.webkitEnterFullscreen;
    if (!f) return;
    try { var p = f.call(el); if (p && p.catch) p.catch(function(){}); } catch (e) {}
  }
  function ctl(v, cmd, s){
    if (cmd === 'toggle') { if (v.paused) v.play(); else v.pause(); }
    else if (cmd === 'play') v.play();
    else if (cmd === 'pause') v.pause();
    else if (cmd === 'seek') {
      var d = isFinite(v.duration) ? v.duration : 1e9;
      v.currentTime = Math.max(0, Math.min(d, v.currentTime + s));
    }
  }
  window.__tvVideo = function(cmd, s){ var v = pick(); if (v) ctl(v, cmd, s); };
  window.__tvState = function(){
    var v = pick(); if (!v) return null;
    return [v.currentTime || 0, isFinite(v.duration) ? v.duration : 0, v.paused];
  };
  window.__tvAfterExit = function(){
    if (lastVideo && !lastVideo.paused) lastVideo.pause();
    if (document.activeElement && document.activeElement.tagName === 'IFRAME') document.activeElement.blur();
    var r = lastRoot || (lastVideo && rootOf(lastVideo));
    if (r) { r.focus(); r.scrollIntoView({block: 'center'}); }
  };

  document.addEventListener('play', function(ev){
    var v = ev.target;
    if (!v || v.tagName !== 'VIDEO' || (v.loop && v.muted)) return;
    lastVideo = v; lastRoot = rootOf(v) || v;
    busyOff();
    if (!isFs()) goFull(v);
  }, true);

  function openPlayer(root){
    lastRoot = root;
    busyOn();
    var v = root.tagName === 'VIDEO' ? root : root.querySelector('video');
    if (v) {
      lastVideo = v;
      var p = v.play(); if (p && p.catch) p.catch(function(){});
      goFull(v);
      return;
    }
    var f = root.tagName === 'IFRAME' ? root : root.querySelector('iframe');
    if (f) { goFull(f); try { f.contentWindow.focus(); } catch (e) {} return; }
    root.click();
  }

  function nav(d){
    var cur = document.activeElement, list = items();
    if (cur) { var pr = rootOf(cur); if (pr) cur = pr; }
    if (!cur || cur === document.body || !visible(cur)) {
      var first = list.filter(function(e){
        var r = e.getBoundingClientRect(); return r.top >= 0 && r.top < innerHeight;
      })[0] || list[0];
      if (first) { first.focus(); first.scrollIntoView({block: 'center'}); }
      return;
    }
    var a = cur.getBoundingClientRect(), ax = a.left + a.width/2, ay = a.top + a.height/2;
    var best = null, bs = 1e9;
    list.forEach(function(e){
      if (e === cur) return;
      var r = e.getBoundingClientRect();
      var dx = r.left + r.width/2 - ax, dy = r.top + r.height/2 - ay;
      var main = dx*d[0] + dy*d[1];
      if (main <= 1) return;
      var side = Math.abs(dx*d[1]) + Math.abs(dy*d[0]);
      var s = main + side*2;
      if (s < bs) { bs = s; best = e; }
    });
    if (best) {
      best.focus();
      best.scrollIntoView({block: 'center', inline: 'center', behavior: 'smooth'});
    } else if (d[1] !== 0) {
      window.scrollBy({top: d[1] * innerHeight * 0.6, behavior: 'smooth'});
    }
  }

  var DIRS = {ArrowUp: [0,-1], ArrowDown: [0,1], ArrowLeft: [-1,0], ArrowRight: [1,0]};

  window.addEventListener('keydown', function(ev){
    if (isFs()) return;
    var cur = document.activeElement, r = cur && rootOf(cur);
    if (!r) return;
    if (ev.key === 'Enter' || ev.key === ' ') openPlayer(r);
    else if (DIRS[ev.key]) nav(DIRS[ev.key]);
    else return;
    ev.preventDefault(); ev.stopImmediatePropagation();
  }, true);

  document.addEventListener('keydown', function(ev){
    if (ev.defaultPrevented || isFs()) return;
    var k = ev.key, cur = document.activeElement, tag = cur ? cur.tagName : '';
    if (k === 'Enter' && cur && cur !== document.body) {
      if (!/^(A|BUTTON|INPUT|SELECT|TEXTAREA)$/.test(tag)) { cur.click(); ev.preventDefault(); }
      return;
    }
    var d = DIRS[k]; if (!d) return;
    if ((tag === 'INPUT' || tag === 'TEXTAREA') && d[1] === 0) return;
    nav(d); ev.preventDefault();
  }, false);

  // بازخورد لودینگ هنگام کلیک روی لینک‌های ناوبری و کنترل‌های سایت
  document.addEventListener('click', function(ev){
    var t = ev.target;
    if (!t || !t.closest) return;
    var a = t.closest('a[href]');
    if (a) {
      if (a.target || (a.getAttribute('href') || '') === '#') return;
      if (/^javascript:/i.test(a.getAttribute('href') || '')) return;
      busyOn();
      return;
    }
    var c = t.closest('[onclick],[role="button"]');
    if (c && !c.closest('[data-tvplayer]')) busyOn();
  }, true);

  window.addEventListener('load', busyOff);
})();
""".trimIndent()
    }

    private lateinit var webView: WebView
    private lateinit var fullscreenContainer: FrameLayout
    private lateinit var splash: View
    private lateinit var splashLogo: ImageView
    private lateinit var splashBar: ProgressBar
    private lateinit var splashPercent: TextView
    private lateinit var busy: View
    private lateinit var busyCard: View

    private lateinit var osd: View
    private lateinit var osdState: TextView
    private lateinit var osdTime: TextView
    private lateinit var osdDuration: TextView
    private lateinit var osdProgress: ProgressBar

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var showingError = false
    private var fsTopVideo = false
    private var splashHidden = false
    private val handler = Handler(Looper.getMainLooper())

    // --- وضعیت لودینگ ---
    private var realProgress = 0
    private var shownProgress = 0f
    private var loadStartedAt = 0L
    private var contentReady = false
    private var pageCommitted = false
    private var navigationStarted = false
    private var busySeq = 0
    private var tickerRunning = false
    private var logoPulse: ObjectAnimator? = null

    private val hideOsdTask = Runnable { osd.visibility = View.GONE }
    private val pollOsdTask = object : Runnable {
        override fun run() {
            if (osd.visibility == View.VISIBLE) { updateOsd(); handler.postDelayed(this, 500) }
        }
    }

    private val progressTicker = object : Runnable {
        override fun run() {
            if (splashHidden) { tickerRunning = false; return }
            val elapsed = (SystemClock.elapsedRealtime() - loadStartedAt) / 1000f
            // تخمین نرم تا رسیدن درصد واقعی؛ سقف ۹۰٪ تا وقتی صفحه تحویل نشده
            val creep = ((1.0 - exp(-elapsed / 5.0)) * 88.0).toFloat()
            val target = when {
                contentReady -> 100f
                realProgress > 0 -> maxOf(realProgress.toFloat(), creep * 0.6f).coerceAtMost(99f)
                else -> creep.coerceAtMost(88f)
            }

            if (target > shownProgress) {
                // وقتی محتوا آماده است، سریع به ۱۰۰٪ می‌رسیم تا کاربر معطل نماند
                val ease = if (contentReady) 0.42f else 0.16f
                shownProgress += (target - shownProgress) * ease
                if (abs(target - shownProgress) < 0.6f) shownProgress = target
            }

            val pct = shownProgress.toInt().coerceIn(0, 100)
            splashBar.progress = pct
            splashPercent.text = "$pct٪"

            if (contentReady && shownProgress >= 99f) { finishSplash(); return }
            handler.postDelayed(this, 70)
        }
    }

    private val busyGuard = Runnable { hideBusyNow() }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        webView = findViewById(R.id.webview)
        fullscreenContainer = findViewById(R.id.fullscreen_container)
        splash = findViewById(R.id.splash)
        splashLogo = findViewById(R.id.splash_logo)
        splashBar = findViewById(R.id.splash_bar)
        splashPercent = findViewById(R.id.splash_percent)
        busy = findViewById(R.id.busy)
        busyCard = findViewById(R.id.busy_card)
        osd = findViewById(R.id.osd)
        osdState = findViewById(R.id.osd_state)
        osdTime = findViewById(R.id.osd_time)
        osdDuration = findViewById(R.id.osd_duration)
        osdProgress = findViewById(R.id.osd_progress)

        // حذف پس‌زمینهٔ سفید لوگو روی صفحهٔ شروع
        if (LOGO_HAS_WHITE_BG) {
            splashLogo.colorFilter = ColorMatrixColorFilter(
                ColorMatrix(floatArrayOf(
                    1f, 0f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1f, 0f, 0f,
                    0f, 0f, -2.2f, 0f, 561f
                ))
            )
        }

        startSplashAnimations()

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            // با wide viewport + overview mode، محتوای عریض‌تر از viewport
            // برای «جا شدن» کوچک می‌شود — دقیقاً همان چیزی که می‌خواهیم.
            loadWithOverviewMode = true
            // مقیاس متنی سیستم را روی وب اعمال نکن (باعث بزرگ‌شدن فونت می‌شود)
            textZoom = 100
            builtInZoomControls = false
            displayZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = TV_UA
        }
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        if (Build.VERSION.SDK_INT >= 26) webView.defaultFocusHighlightEnabled = false

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
            listOf("https://www.anobis.site", "https://anobis.site", "https://tv.anobis.site").forEach {
                setCookie(it, "dle_skin=smart; path=/; max-age=31536000")
            }
            flush()
        }

        webView.addJavascriptInterface(BusyBridge(), "AnobisBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = false

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                if (splashHidden) {
                    navigationStarted = true
                    pageCommitted = false
                    armBusy()
                } else if (!isBootstrap(url)) {
                    // هر صفحهٔ واقعی جدید، درصد را از صفر دنبال می‌کند
                    realProgress = 0
                }
            }

            // اولین نقاشی صفحه: زودترین لحظه‌ای که کاربر محتوا را می‌بیند
            override fun onPageCommitVisible(view: WebView, url: String) {
                navigationStarted = false
                pageCommitted = true
                if (!isBootstrap(url)) contentReady = true
                startTicker()
                // صفحه پس از اعمال viewport دوباره چیده می‌شود؛ اسپینر را تا
                // پایان این چیدمان نگه می‌داریم تا فلش بزرگ‌نمایی دیده نشود.
                handler.postDelayed({ hideBusyNow() }, 220)
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (isBootstrap(url)) {
                    // اسکین یک‌بار ست شد؛ دفعهٔ بعد مستقیم آدرس اصلی باز می‌شود
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putBoolean(KEY_SKIN_OK, true).apply()
                } else {
                    view.evaluateJavascript(TV_JS, null)
                    contentReady = true
                }
                navigationStarted = false
                pageCommitted = true
                // همان دلیلی که در onPageCommitVisible هست: تا چیدمان دوبارهٔ
                // صفحه تمام نشود اسپینر را برنمی‌داریم.
                handler.postDelayed({ hideBusyNow() }, 220)
                startTicker()
                if (customView == null) view.requestFocus()
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame && !splashHidden && response.statusCode >= 400) {
                    // سرور پاسخ خطا داد؛ صفحهٔ شروع را نبندیم تا کاربر منتظر نماند
                    finishSplash()
                }
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    showingError = true
                    navigationStarted = false
                    pageCommitted = true
                    hideBusyNow()
                    finishSplash()
                    view.loadDataWithBaseURL(null,
                        "<html dir='rtl'><body style='background:#141824;color:#fff;font-family:sans-serif;" +
                        "font-size:34px;text-align:center;padding-top:18%'>اتصال برقرار نشد" +
                        "<br><br><span style='font-size:24px;color:#aaa'>دکمهٔ OK را برای تلاش دوباره بزنید</span>" +
                        "</body></html>", "text/html", "UTF-8", null)
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                if (!isBootstrap(view.url)) {
                    realProgress = newProgress
                    if (newProgress >= 100) contentReady = true
                }
                startTicker()
            }

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (customView != null) { callback.onCustomViewHidden(); return }
                hideBusyNow()
                customView = view
                customViewCallback = callback
                fullscreenContainer.addView(view, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                fullscreenContainer.visibility = View.VISIBLE
                view.requestFocus()
                fsTopVideo = false
                handler.postDelayed({
                    if (customView == null) return@postDelayed
                    webView.evaluateJavascript(
                        "(function(){var f=document.fullscreenElement||document.webkitFullscreenElement;" +
                        "return !!(f&&(f.tagName==='VIDEO'||f.querySelector('video')));})()"
                    ) { r -> fsTopVideo = r == "true"; if (fsTopVideo) showOsd() }
                }, 300)
            }

            override fun onHideCustomView() = exitFullscreen()

            override fun getDefaultVideoPoster(): Bitmap =
                Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }

        loadStartedAt = SystemClock.elapsedRealtime()
        startTicker()

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
            finishSplash()
        } else {
            webView.loadUrl(startUrl())
        }
        webView.requestFocus()

        // سقف ایمنی: صفحهٔ شروع هرگز بیش از ۱۲ ثانیه بسته نماند
        handler.postDelayed({ finishSplash() }, 12000)
    }

    /**
     * لینک تغییر اسکین فقط یک‌بار (اولین اجرا) لازم است؛ بعد از آن کوکی روی دیسک
     * ذخیره شده و بارگذاری آدرس اصلی یک رفت‌وبرگشت کامل شبکه را حذف می‌کند.
     */
    private fun startUrl(): String {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        return if (prefs.getBoolean(KEY_SKIN_OK, false)) SITE_URL else SKIN_REDIRECT_URL
    }

    private fun startSplashAnimations() {
        // نبض ملایم لوگو
        logoPulse = ObjectAnimator.ofPropertyValuesHolder(
            splashLogo,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.05f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.05f)
        ).apply {
            duration = 1500
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            start()
        }
    }

    /** صفحهٔ تغییر اسکین یک ریدایرکت واسط است؛ نباید لودینگ اصلی را تمام کند. */
    private fun isBootstrap(url: String?): Boolean =
        url != null && url.contains("action_skin_change")


    private fun startTicker() {
        if (splashHidden || tickerRunning) return
        tickerRunning = true
        handler.post(progressTicker)
    }

    private fun finishSplash() {
        if (splashHidden) return
        splashHidden = true
        tickerRunning = false
        handler.removeCallbacks(progressTicker)
        splashBar.progress = 100
        splashPercent.text = "100٪"
        splash.animate().alpha(0f).setDuration(280)
            .withEndAction {
                splash.visibility = View.GONE
                logoPulse?.cancel()
                if (customView == null) webView.requestFocus()
            }.start()
    }

    // ---------- لودینگ داخل برنامه ----------

    private fun armBusy() {
        if (!splashHidden || customView != null) return
        pageCommitted = false
        busySeq++
        showBusyNow()
    }

    private fun showBusyNow() {
        if (!splashHidden || customView != null) return
        busy.animate().cancel()
        busy.visibility = View.VISIBLE
        busy.alpha = 0f
        busy.animate().alpha(1f).setDuration(140).start()
        handler.removeCallbacks(busyGuard)
        handler.postDelayed(busyGuard, 20000)
    }

    private fun hideBusyNow() {
        busySeq++
        handler.removeCallbacks(busyGuard)
        if (busy.visibility != View.VISIBLE) return
        busy.animate().cancel()
        busy.animate().alpha(0f).setDuration(120)
            .withEndAction { busy.visibility = View.GONE }.start()
    }

    /**
     * پل جاوااسکریپت: فقط نمایش/پنهان‌سازی لودینگ.
     * اگر کلیک منجر به ناوبری نشود، لودینگ خودش جمع می‌شود.
     */
    inner class BusyBridge {
        @JavascriptInterface
        fun loading() {
            runOnUiThread {
                if (!splashHidden || customView != null) return@runOnUiThread
                val seq = ++busySeq
                showBusyNow()
                // اگر تا ۱.۲ ثانیه ناوبری واقعی شروع نشد، کلیک کار دیگری می‌کرد
                handler.postDelayed({
                    if (seq == busySeq && !navigationStarted && !pageCommitted) hideBusyNow()
                }, 1200)
            }
        }

        @JavascriptInterface
        fun ready() {
            runOnUiThread { hideBusyNow() }
        }
    }

    // ---------- پخش ----------

    private fun exitFullscreen() {
        val v = customView ?: return
        handler.removeCallbacks(hideOsdTask)
        handler.removeCallbacks(pollOsdTask)
        osd.visibility = View.GONE
        fullscreenContainer.removeView(v)
        fullscreenContainer.visibility = View.GONE
        customView = null
        fsTopVideo = false
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        webView.requestFocus()
        webView.evaluateJavascript("window.__tvAfterExit && window.__tvAfterExit()", null)
    }

    private fun video(cmd: String) =
        webView.evaluateJavascript("window.__tvVideo && window.__tvVideo($cmd)", null)

    private fun showOsd() {
        if (customView == null || !fsTopVideo) return
        osd.visibility = View.VISIBLE
        handler.removeCallbacks(pollOsdTask); handler.post(pollOsdTask)
        handler.removeCallbacks(hideOsdTask); handler.postDelayed(hideOsdTask, 4000)
    }

    private fun updateOsd() {
        webView.evaluateJavascript("window.__tvState ? window.__tvState() : null") { r ->
            try {
                val a = JSONArray(r)
                val cur = a.getDouble(0)
                val dur = a.getDouble(1)
                val paused = a.getBoolean(2)
                osdState.text = if (paused) "❚❚" else "▶"
                osdTime.text = fmt(cur)
                osdDuration.text = fmt(dur)
                osdProgress.progress = if (dur > 0) (cur / dur * 1000).toInt() else 0
                if (paused) handler.removeCallbacks(hideOsdTask)
            } catch (_: Exception) { }
        }
    }

    private fun fmt(sec: Double): String {
        val s = sec.toLong().coerceAtLeast(0)
        val h = s / 3600; val m = (s % 3600) / 60; val ss = s % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, ss)
        else String.format(Locale.US, "%02d:%02d", m, ss)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val down = event.action == KeyEvent.ACTION_DOWN
        val code = event.keyCode

        val mediaCmd = when (code) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "'toggle',0"
            KeyEvent.KEYCODE_MEDIA_PLAY -> "'play',0"
            KeyEvent.KEYCODE_MEDIA_PAUSE -> "'pause',0"
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> "'seek',$SEEK_SECONDS"
            KeyEvent.KEYCODE_MEDIA_REWIND -> "'seek',-$SEEK_SECONDS"
            else -> null
        }
        if (mediaCmd != null) { if (down) { video(mediaCmd); showOsd() }; return true }

        if (code == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) {
                when {
                    customView != null -> exitFullscreen()
                    webView.canGoBack() && !showingError -> {
                        armBusy()
                        webView.goBack()
                    }
                    else -> finish()
                }
            }
            return true
        }

        if (customView != null) {
            if (fsTopVideo) {
                when (code) {
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        if (down && event.repeatCount == 0) { video("'toggle',0"); showOsd() }
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (down) { video("'seek',-$SEEK_SECONDS"); showOsd() }
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (down) { video("'seek',$SEEK_SECONDS"); showOsd() }
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (down) showOsd()
                        return true
                    }
                }
            } else if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
                return super.dispatchKeyEvent(KeyEvent(event.downTime, event.eventTime,
                    event.action, KeyEvent.KEYCODE_SPACE, event.repeatCount))
            }
            return super.dispatchKeyEvent(event)
        }

        when (code) {
            KeyEvent.KEYCODE_MENU -> {
                if (down) { armBusy(); webView.reload() }
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> if (showingError) {
                if (down) {
                    showingError = false
                    splashHidden = true
                    armBusy()
                    webView.loadUrl(startUrl())
                }
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onResume() { super.onResume(); webView.onResume() }
    override fun onPause() {
        webView.onPause()
        CookieManager.getInstance().flush()
        super.onPause()
    }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        logoPulse?.cancel()
        webView.destroy()
        super.onDestroy()
    }
}
