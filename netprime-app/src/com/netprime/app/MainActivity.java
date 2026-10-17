package com.netprime.app;

import android.app.Activity;
import android.app.UiModeManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class MainActivity extends Activity {

    static final String HOME_URL = "https://netprime.bz/";

    // Pages may only open on these sites. Everything else (ad redirects) is ignored.
    static final String[] ALLOWED_SITES = { "netprime.bz" };

    private CursorLayout root;
    private WebView webView;
    private Ui.TopProgress progress;
    private Ui.Splash splash;
    private Ui.Hint hint;
    private View retryButton;
    private View errorView;
    private View fullscreenView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean tv;
    private String navJs = "";
    private boolean okHeld;
    private long lastMove;
    private boolean okLongPressed;
    private final Runnable okLongPress = new Runnable() {
        @Override
        public void run() {
            okLongPressed = true;
            togglePointerMode();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_FULLSCREEN);

        AdBlock.load(this);

        Ui.loadFonts(this);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);

        root = new CursorLayout(this);
        root.setBackgroundColor(Ui.BG);
        tv = isTv();
        navJs = readAsset("tvnav.js");
        root.setPointerMode(false);

        webView = new WebView(this);
        setupWebView();
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.setScrollTarget(webView);

        progress = new Ui.TopProgress(this);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3));
        plp.gravity = Gravity.TOP;
        root.addView(progress, plp);

        View[] first = new View[1];
        errorView = Ui.errorScreen(this, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                errorView.setVisibility(View.GONE);
                webView.reload();
            }
        }, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        }, first);
        retryButton = first[0];
        errorView.setVisibility(View.GONE);
        root.addView(errorView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        hint = new Ui.Hint(this);
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.gravity = Gravity.BOTTOM;
        hlp.bottomMargin = dp(40);
        root.addView(hint, hlp);

        if (savedInstanceState == null) {
            splash = new Ui.Splash(this);
            splash.setOnGone(new Runnable() {
                @Override
                public void run() {
                    splash = null;
                    if (tv) hint.show("Arrows  Move   \u00B7   OK  Open   \u00B7   Hold OK  Pointer", 6000);
                }
            });
            root.addView(splash, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        setContentView(root);

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl(HOME_URL);
        }
    }

    private void dismissSplash() {
        if (splash != null) splash.dismiss();
    }

    private void showError() {
        dismissSplash();
        errorView.setAlpha(0f);
        errorView.setVisibility(View.VISIBLE);
        errorView.animate().alpha(1f).setDuration(250).start();
        retryButton.requestFocus();
    }

    // Small visual polish on the website itself: no scrollbars, no grey tap flash.
    private static final String BASE_CSS =
            "(function(){if(document.getElementById('__np_css'))return;"
            + "var s=document.createElement('style');s.id='__np_css';"
            + "s.textContent='::-webkit-scrollbar{width:0!important;height:0!important;"
            + "background:transparent!important}*{-webkit-tap-highlight-color:transparent}';"
            + "(document.head||document.documentElement).appendChild(s);})();";

    private String readAsset(String name) {
        try {
            InputStream in = getAssets().open(name);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            return out.toString("UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private boolean isTv() {
        UiModeManager ui = (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
        if (ui != null && ui.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) return true;
        return getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK);
    }

    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        // Block pop-up windows.
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setSupportMultipleWindows(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        // Smoother scrolling: draw the parts of the page just off screen ahead of time,
        // and give the page renderer high priority.
        if (Build.VERSION.SDK_INT >= 23) s.setOffscreenPreRaster(true);
        if (Build.VERSION.SDK_INT >= 26) {
            webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true);
        }
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setBackgroundColor(Color.BLACK);
        webView.setWebViewClient(new Client());
        webView.setWebChromeClient(new Chrome());
    }

    static boolean isAllowedSite(String host) {
        if (host == null) return false;
        host = host.toLowerCase();
        for (String site : ALLOWED_SITES) {
            if (host.equals(site) || host.endsWith("." + site)) return true;
        }
        return false;
    }

    private class Client extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return blockNavigation(request.getUrl());
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return blockNavigation(Uri.parse(url));
        }

        private boolean blockNavigation(Uri uri) {
            String scheme = uri.getScheme();
            if (!"http".equals(scheme) && !"https".equals(scheme)) return true;
            return !isAllowedSite(uri.getHost());
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            if (AdBlock.isAd(request.getUrl().getHost())) return AdBlock.emptyResponse();
            return super.shouldInterceptRequest(view, request);
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            errorView.setVisibility(View.GONE);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            view.evaluateJavascript(BASE_CSS, null);
            if (tv) view.evaluateJavascript(navJs, null);
            dismissSplash();
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request.isForMainFrame()) showError();
        }
    }

    private class Chrome extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int p) {
            progress.setProgress(p);
            if (p >= 100) dismissSplash();
        }

        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (fullscreenView != null) {
                callback.onCustomViewHidden();
                return;
            }
            fullscreenView = view;
            fullscreenCallback = callback;
            // Add below the cursor (the cursor is drawn on top of all children).
            root.addView(view, 1, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            view.setAlpha(0f);
            view.animate().alpha(1f).setDuration(220).start();
            webView.setVisibility(View.GONE);
            hideSystemBars();
        }

        @Override
        public void onHideCustomView() {
            exitFullscreen();
        }
    }

    private void exitFullscreen() {
        if (fullscreenView == null) return;
        root.removeView(fullscreenView);
        fullscreenView = null;
        webView.setAlpha(0f);
        webView.setVisibility(View.VISIBLE);
        webView.animate().alpha(1f).setDuration(220).start();
        if (fullscreenCallback != null) fullscreenCallback.onCustomViewHidden();
        fullscreenCallback = null;
    }

    @SuppressWarnings("deprecation")
    private void hideSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    // ---- TV remote ----

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;

        if (code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || code == KeyEvent.KEYCODE_MEDIA_PLAY
                || code == KeyEvent.KEYCODE_MEDIA_PAUSE) {
            if (down && event.getRepeatCount() == 0) playPause();
            return true;
        }
        if (!tv || errorView.getVisibility() == View.VISIBLE) return super.dispatchKeyEvent(event);

        if (isOk(code)) {
            if (down) {
                if (event.getRepeatCount() == 0) {
                    okHeld = true;
                    okLongPressed = false;
                    handler.postDelayed(okLongPress, 700);
                }
            } else if (okHeld) {
                okHeld = false;
                handler.removeCallbacks(okLongPress);
                if (!okLongPressed) click();
            }
            return true;
        }

        // Pointer mode: the root view moves the pointer.
        if (root.isPointerMode()) return super.dispatchKeyEvent(event);

        String dir = direction(code);
        if (dir == null) return super.dispatchKeyEvent(event);

        if (fullscreenView != null) {
            // Fullscreen video: left/right seek, up/down go to the player.
            if (down && (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT)) {
                final int keyCode = code;
                js("__tvnav.seek(" + (code == KeyEvent.KEYCODE_DPAD_LEFT ? -10 : 10) + ")",
                        new ValueCallback<String>() {
                            @Override
                            public void onReceiveValue(String handled) {
                                if (!"true".equals(handled)) sendKeyToPage(keyCode);
                            }
                        });
            } else if (down) {
                sendKeyToPage(code);
            }
            return true;
        }

        if (down) {
            // Holding an arrow moves at a steady pace instead of as fast as the remote repeats.
            long now = SystemClock.uptimeMillis();
            if (event.getRepeatCount() > 0 && now - lastMove < 150) return true;
            lastMove = now;
            js("__tvnav.move('" + dir + "')", null);
        }
        return true;
    }

    private static boolean isOk(int code) {
        return code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    private static String direction(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP: return "up";
            case KeyEvent.KEYCODE_DPAD_DOWN: return "down";
            case KeyEvent.KEYCODE_DPAD_LEFT: return "left";
            case KeyEvent.KEYCODE_DPAD_RIGHT: return "right";
            default: return null;
        }
    }

    /** OK button: open the highlighted item (or tap under the pointer). */
    private void click() {
        if (root.isPointerMode()) {
            root.tapAtPointer();
        } else if (fullscreenView != null) {
            root.tap(root.getWidth() / 2f, root.getHeight() / 2f);
        } else {
            js("__tvnav.target()", new ValueCallback<String>() {
                @Override
                public void onReceiveValue(String value) {
                    tapTarget(value);
                }
            });
        }
    }

    private void tapTarget(String json) {
        if (json == null || "null".equals(json)) {
            js("__tvnav.move('down')", null); // nothing selected yet: select something first
            return;
        }
        try {
            JSONObject o = new JSONObject(json);
            float x = webView.getLeft() + (float) o.getDouble("fx") * webView.getWidth();
            float y = webView.getTop() + (float) o.getDouble("fy") * webView.getHeight();
            root.tap(x, y);
        } catch (Exception ignored) {
        }
    }

    private void togglePointerMode() {
        boolean pointer = !root.isPointerMode();
        root.setPointerMode(pointer);
        if (pointer) js("__tvnav.clear()", null);
        hint.show(pointer ? "Pointer mode  \u00B7  Hold OK to switch back" : "Remote mode", 2500);
    }

    private void playPause() {
        js("__tvnav.playPause()", new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String handled) {
                // Video inside an embedded player: tapping the middle usually toggles it.
                if (!"true".equals(handled) && fullscreenView != null) {
                    root.tap(root.getWidth() / 2f, root.getHeight() / 2f);
                }
            }
        });
    }

    private void sendKeyToPage(int code) {
        webView.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
        webView.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code));
    }

    /** Runs a navigation call; the full script is only sent when the page doesn't have it yet. */
    private void js(final String call, final ValueCallback<String> callback) {
        webView.evaluateJavascript("window.__tvnav?(" + call + "):'__missing'",
                new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String v) {
                        if ("\"__missing\"".equals(v)) {
                            webView.evaluateJavascript(navJs + "\n" + call, callback);
                        } else if (callback != null) {
                            callback.onReceiveValue(v);
                        }
                    }
                });
    }

    @Override
    public void onBackPressed() {
        if (errorView.getVisibility() == View.VISIBLE) {
            finish();
        } else if (fullscreenView != null) {
            exitFullscreen();
        } else if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    protected void onPause() {
        super.onPause();
        webView.onPause();
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    protected void onDestroy() {
        root.removeView(webView);
        webView.destroy();
        super.onDestroy();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
