package com.anobis.tv

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

class MainActivity : Activity() {

    companion object {
        const val START_URL =
            "https://www.anobis.site/index.php?action_skin_change=yes&skin_name=smart"
        const val TV_UA =
            "Mozilla/5.0 (SMART-TV; Linux; Tizen 6.0) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/4.0 Chrome/76.0.3809.146 TV Safari/537.36"
        const val SEEK_SECONDS = 5

        // اگر لوگو را با پس‌زمینهٔ شفاف آپلود کردید، false کنید
        const val LOGO_HAS_WHITE_BG = true

        private val TV_JS = """
(function(){
  if (window.__tv) return; window.__tv = 1;

  var userMoved = false;
  window.addEventListener('keydown', function(){ userMoved = true; }, true);

  var st = document.createElement('style');
  st.textContent = ':focus{outline:4px solid #D9B98C !important;outline-offset:3px;border-radius:4px}';
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
    return (s || '').replace(/ي/g, 'ی').replace(/\u200c/g, ' ').replace(/\s+/g, ' ').trim();
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
    if (!isFs()) goFull(v);
  }, true);

  function openPlayer(root){
    lastRoot = root;
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
})();
""".trimIndent()
    }

    private lateinit var webView: WebView
    private lateinit var fullscreenContainer: FrameLayout
    private lateinit var splash: View
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

    private val hideOsdTask = Runnable { osd.visibility = View.GONE }
    private val pollOsdTask = object : Runnable {
        override fun run() {
            if (osd.visibility == View.VISIBLE) { updateOsd(); handler.postDelayed(this, 500) }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        webView = findViewById(R.id.webview)
        fullscreenContainer = findViewById(R.id.fullscreen_container)
        splash = findViewById(R.id.splash)
        osd = findViewById(R.id.osd)
        osdState = findViewById(R.id.osd_state)
        osdTime = findViewById(R.id.osd_time)
        osdDuration = findViewById(R.id.osd_duration)
        osdProgress = findViewById(R.id.osd_progress)

        // حذف پس‌زمینهٔ سفید لوگو روی صفحهٔ شروع
        if (LOGO_HAS_WHITE_BG) {
            findViewById<ImageView>(R.id.splash_logo).colorFilter = ColorMatrixColorFilter(
                ColorMatrix(floatArrayOf(
                    1f, 0f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1f, 0f, 0f,
                    0f, 0f, -2.2f, 0f, 561f
                ))
            )
        }

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
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

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = false

            override fun onPageFinished(view: WebView, url: String) {
                view.evaluateJavascript(TV_JS, null)
                if (!url.contains("action_skin_change")) hideSplash()
                if (customView == null) view.requestFocus()
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    showingError = true
                    hideSplash()
                    view.loadDataWithBaseURL(null,
                        "<html dir='rtl'><body style='background:#141824;color:#fff;font-family:sans-serif;" +
                        "font-size:34px;text-align:center;padding-top:18%'>اتصال برقرار نشد" +
                        "<br><br><span style='font-size:24px;color:#aaa'>دکمهٔ OK را برای تلاش دوباره بزنید</span>" +
                        "</body></html>", "text/html", "UTF-8", null)
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (customView != null) { callback.onCustomViewHidden(); return }
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

        if (savedInstanceState != null) webView.restoreState(savedInstanceState)
        else webView.loadUrl(START_URL)
        webView.requestFocus()

        handler.postDelayed({ hideSplash() }, 20000)
    }

    private fun hideSplash() {
        if (splashHidden) return
        splashHidden = true
        splash.animate().alpha(0f).setDuration(400)
            .withEndAction { splash.visibility = View.GONE }.start()
        if (customView == null) webView.requestFocus()
    }

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
                    webView.canGoBack() && !showingError -> webView.goBack()
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
            KeyEvent.KEYCODE_MENU -> { if (down) webView.reload(); return true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> if (showingError) {
                if (down) { showingError = false; webView.loadUrl(START_URL) }
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
        webView.destroy()
        super.onDestroy()
    }
}
