package com.aroha.astrology;

import android.util.Log;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebView;
import com.getcapacitor.Bridge;
import com.getcapacitor.WebViewListener;

/**
 * Survives the WebView's renderer process being killed instead of dying with it.
 *
 * This app is a webview shell: the whole UI is one WebView pointed at
 * app.arohaastrology.in (capacitor.config.ts). Chromium runs that page in a
 * separate renderer process, and when the renderer is killed — OOM on a heavy
 * page, or the system reclaiming memory while backgrounded — Android calls
 * WebViewClient.onRenderProcessGone. Returning false there means "not handled",
 * and the documented consequence is that the system kills the entire app
 * process: to the user the app simply vanishes, with no crash dialog.
 *
 * Capacitor's BridgeWebViewClient forwards that callback to every registered
 * WebViewListener and returns false when none handles it — and this app
 * registered none, so every renderer death was an app death. That is what "the
 * app closes when I open it" looked like from the outside.
 *
 * A dead WebView can never be reused (every later call on it throws) and
 * Capacitor exposes no way to swap in a fresh one — Bridge has setWebViewClient
 * but no setWebView. So recovery is Activity.recreate(): MainActivity is torn
 * down and rebuilt, which builds a new Bridge and a new WebView from scratch.
 * The user sees the app reload rather than disappear.
 */
public class RendererRecoveryListener extends WebViewListener {

    private static final String TAG = "RendererRecovery";

    /**
     * If the renderer dies again this soon after a recovery, the page is
     * reliably killing itself and recreate() would just spin. Hand back to the
     * platform instead so the process dies once, visibly, rather than looping.
     */
    private static final long RECOVERY_COOLDOWN_MS = 30_000;

    private final Bridge bridge;
    private long lastRecoveryAt = 0;

    public RendererRecoveryListener(Bridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public boolean onRenderProcessGone(WebView webView, RenderProcessGoneDetail detail) {
        // didCrash() false means the system reclaimed the renderer rather than
        // it crashing. Worth having in a bug report; recovery is the same.
        long now = System.currentTimeMillis();
        boolean looping = now - lastRecoveryAt < RECOVERY_COOLDOWN_MS;
        Log.w(
            TAG,
            "WebView renderer gone (didCrash=" + detail.didCrash() + ", looping=" + looping + ")"
        );

        if (looping) return false;
        lastRecoveryAt = now;

        try {
            bridge.getActivity().recreate();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "recreate() failed; falling back to platform default", e);
            return false;
        }
    }
}
