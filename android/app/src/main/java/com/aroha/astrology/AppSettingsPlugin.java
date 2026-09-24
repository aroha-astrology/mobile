package com.aroha.astrology;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * Opens this app's own notification settings screen. Once a user has declined
 * the Android 13+ notification permission twice, the system dialog never shows
 * again — requestPermissions() just returns "denied" — so the only way back is
 * the settings screen. The web layer (frontend lib/app-settings.ts) uses this
 * from the "turn notifications back on" prompt.
 *
 * Uses getPackageName() rather than a hard-coded id, so it always opens the
 * page for whichever build is running.
 */
@CapacitorPlugin(name = "AppSettings")
public class AppSettingsPlugin extends Plugin {

    @PluginMethod
    public void openNotificationSettings(final PluginCall call) {
        final String packageName = getContext().getPackageName();
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName);
        } else {
            intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + packageName));
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            getContext().startActivity(intent);
            call.resolve();
        } catch (ActivityNotFoundException e) {
            // Some OEM builds strip the per-app notification screen — fall back to app details.
            try {
                Intent details = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + packageName));
                details.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(details);
                call.resolve();
            } catch (ActivityNotFoundException e2) {
                call.reject("No settings screen available");
            }
        }
    }
}
