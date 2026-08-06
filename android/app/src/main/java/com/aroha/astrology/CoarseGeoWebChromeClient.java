package com.aroha.astrology;

import android.Manifest;
import android.content.pm.PackageManager;
import android.webkit.GeolocationPermissions;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import com.getcapacitor.Bridge;
import com.getcapacitor.BridgeWebChromeClient;

/**
 * Capacitor's own BridgeWebChromeClient.onGeolocationPermissionsShowPrompt asks for
 * BOTH ACCESS_COARSE_LOCATION and ACCESS_FINE_LOCATION on every navigator.geolocation
 * call, unconditionally. This app ships coarse-only on purpose (see AndroidManifest.xml),
 * so that request includes a permission the APK never declared — AOSP just answers
 * "denied", but OEM permission controllers are not uniform about it, which is how a
 * location prompt turns into a crash on some devices and not others.
 *
 * Capacitor already guards against exactly this a few methods over — isMediaCaptureSupported()
 * calls PermissionHelper.hasDefinedPermission() before requesting CAMERA — the geolocation
 * path just never got the same check. So: ask for what the manifest actually declares.
 */
public class CoarseGeoWebChromeClient extends BridgeWebChromeClient {

    private final Bridge bridge;
    private final ActivityResultLauncher<String> coarseLauncher;
    private GeolocationPermissions.Callback pendingCallback;
    private String pendingOrigin;

    public CoarseGeoWebChromeClient(Bridge bridge) {
        super(bridge);
        this.bridge = bridge;
        // Must be registered before the Activity reaches STARTED, hence construction
        // from MainActivity.onCreate() rather than lazily on first geolocation call.
        this.coarseLauncher =
            bridge.registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (pendingCallback == null) return;
                    pendingCallback.invoke(pendingOrigin, granted, false);
                    pendingCallback = null;
                    pendingOrigin = null;
                }
            );
    }

    @Override
    public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
        // Deliberately does NOT call super — super is the two-permission version.
        boolean granted =
            ContextCompat.checkSelfPermission(bridge.getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED;

        if (granted) {
            callback.invoke(origin, true, false);
            return;
        }

        pendingOrigin = origin;
        pendingCallback = callback;
        coarseLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION);
    }
}
