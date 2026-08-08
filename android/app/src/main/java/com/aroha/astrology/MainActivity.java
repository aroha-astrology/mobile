package com.aroha.astrology;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(PlayBillingPlugin.class);
        registerPlugin(TtsPlugin.class);
        registerPlugin(AppReviewPlugin.class);
        super.onCreate(savedInstanceState);
        // Replaces the WebChromeClient Bridge installs in initWebView(), purely to
        // narrow the geolocation permission request to the one this app declares.
        getBridge().getWebView().setWebChromeClient(new CoarseGeoWebChromeClient(getBridge()));
        // Without a listener here, Capacitor's onRenderProcessGone returns false and
        // Android kills the whole app process when the webview renderer dies.
        getBridge().addWebViewListener(new RendererRecoveryListener(getBridge()));
    }
}
