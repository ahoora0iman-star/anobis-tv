package com.anobis.tv

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.*
import android.widget.FrameLayout

class MainActivity : Activity() {

    companion object {
        const val START_URL =
            "https://www.anobis.site/index.php?action_skin_change=yes&skin_name=smart"
        const val SEEK_SECONDS = 10

        // ناوبری با ریموت + کنترل ویدیو داخل صفحه
        private val TV_JS = """
(function(){
  if (window.__tv) return; window.__tv = 1;
  var st = document.createElement('style');
  st.textContent = ':focus{outline:4px solid #4af !important;outline-offset:3px;border-radius:4px}';
  (document.head || document.documentElement).appendChild(st);

  var SEL = 'a[href],button,input,select,textarea,video,[onclick],[role="button"],[tabindex]:not([tabindex="-1"])';
  function visible(e){
    var r = e.getBoundingClientRect();
    if (r.width < 2 || r.height < 2) return false;
    var s = getComputedStyle(e);
    return s.visibility !== 'hidden' && s.display !== 'none' && !e.disabled;
  }
  function items(){ return [].filter.call(document.querySelectorAll(SEL), visible); }

  var t = null;
  function prep(){
    [].forEach.call(document.querySelectorAll('[onclick],[role="button"]'), function(e){
      if (!e.hasAttribute('tabindex')) e.setAttribute('tabindex','0');
    });
  }
  prep();
  new MutationObserver(function(){ clearTimeout(t); t = setTimeout(prep, 300); })
    .observe(document.documentElement, {childList:true, subtree:true});

  function ctl(v, cmd, s){
    if (cmd === 'toggle') { if (v.paused) v.play(); else v.pause(); }
    else if (cmd === 'play') v.play();
    else if (cmd === 'pause') v.pause();
    else if (cmd === 'seek') {
      var d = isFinite(v.duration) ? v.duration : 1e9;
      v.currentTime = Math.max(0, Math.min(d, v.currentTime + s));
    }
  }
  function fsVideo(){
    var f = document.fullscreenElement || document.webkitFullscreenElement;
    if (!f) return null;
    return f.tagName === 'VIDEO' ? f : f.querySelector('video');
  }
  window.__tvVideo = function(cmd, s){
    var v = fsVideo();
    if (!v) {
      var vs = [].slice.call(document.querySelectorAll('video'));
      v = vs.filter(function(x){ return !x.paused; })[0] || vs[0];
    }
    if (v) ctl(v, cmd, s);
  };

  document.addEventListener('keydown', function(ev){
    if (ev.defaultPrevented) return;
    var k = ev.key;

    var fv = fsVideo();
    if (fv) {
      if (k === 'Enter' || k === ' ') { ctl(fv,'toggle'); ev.preventDefault(); }
      else if (k === 'ArrowRight') { ctl(fv,'seek',10); ev.preventDefault(); }
      else if (k === 'ArrowLeft') { ctl(fv,'seek',-10); ev.preventDefault(); }
      return;
    }

    var cur = document.activeElement;
    var tag = cur ? cur.tagName : '';

    if (k === 'Enter' && cur && cur !== document.body) {
      if (tag === 'VIDEO') { ctl(cur,'toggle'); ev.preventDefault(); }
      else if (!/^(A|BUTTON|INPUT|SELECT|TEXTAREA)$/.test(tag)) { cur.click(); ev.preventDefault(); }
      return;
    }

    var d = {ArrowUp:[0,-1],ArrowDown:[0,1],ArrowLeft:[-1,0],ArrowRight:[1,0]}[k];
    if (!d) return;
    if ((tag === 'INPUT' || tag === 'TEXTAREA') && d[1] === 0) return;
    if (tag === 'VIDEO' && d[1] === 0) { ctl(cur,'seek', d[0]*10); ev.preventDefault(); return; }

    var list = items();
    if (!cur || cur === document.body) {
      if (list[0]) { list[0].focus(); list[0].scrollIntoView({block:'center'}); ev.preventDefault(); }
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
      best.scrollIntoView({block:'center', inline:'center', behavior:'smooth'});
    } else if (d[1] !== 0) {
      window.scrollBy({top: d[1]*innerHeight*0.6, behavior:'smooth'});
    }
    ev.preventDefault();
  }, false);
})();
""".trimIndent()
    }

    private lateinit var webView: WebView
    private lateinit var fullscreenContainer: FrameLayout
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var showingError = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        webView = findViewById(R.id.webview)
        fullscreenContainer = findViewById(R.id.fullscreen_container)

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = "$userAgentString AndroidTV"
        }
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        if (Build.VERSION.SDK_INT >= 26) webView.defaultFocusHighlightEnabled = false

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = false

            override fun onPageFinished(view: WebView, url: String) {
                view.evaluateJavascript(TV_JS, null)
                view.requestFocus()
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    showingError = true
                    view.loadDataWithBaseURL(null,
                        "<html dir='rtl'><body style='background:#000;color:#fff;font-family:sans-serif;" +
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
                fullscreenContainer.addView(view)
                fullscreenContainer.visibility = View.VISIBLE
                webView.visibility = View.GONE
                view.requestFocus()
            }

            override fun onHideCustomView() = exitFullscreen()

            // حذف آیکون خاکستری پیش‌فرض روی ویدیوها
            override fun getDefaultVideoPoster(): Bitmap =
                Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }

        if (savedInstanceState != null) webView.restoreState(savedInstanceState)
        else webView.loadUrl(START_URL)
        webView.requestFocus()
    }

    private fun exitFullscreen() {
        val v = customView ?: return
        fullscreenContainer.removeView(v)
        fullscreenContainer.visibility = View.GONE
        webView.visibility = View.VISIBLE
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        webView.requestFocus()
    }

    private fun video(cmd: String) =
        webView.evaluateJavascript("window.__tvVideo && window.__tvVideo($cmd)", null)

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val down = event.action == KeyEvent.ACTION_DOWN

        val mediaCmd = when (event.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "'toggle',0"
            KeyEvent.KEYCODE_MEDIA_PLAY -> "'play',0"
            KeyEvent.KEYCODE_MEDIA_PAUSE -> "'pause',0"
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> "'seek',$SEEK_SECONDS"
            KeyEvent.KEYCODE_MEDIA_REWIND -> "'seek',-$SEEK_SECONDS"
            else -> null
        }
        if (mediaCmd != null) { if (down) video(mediaCmd); return true }

        when (event.keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                if (event.action == KeyEvent.ACTION_UP) {
                    when {
                        customView != null -> exitFullscreen()
                        webView.canGoBack() && !showingError -> webView.goBack()
                        else -> finish()
                    }
                }
                return true
            }
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
    override fun onDestroy() { webView.destroy(); super.onDestroy() }
}
