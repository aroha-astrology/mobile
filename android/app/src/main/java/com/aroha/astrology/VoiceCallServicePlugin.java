package com.aroha.astrology;

import android.content.Context;
import android.content.Intent;
import androidx.core.content.ContextCompat;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * Lets the web app start and stop VoiceCallService, so a voice call carries on
 * while the app is minimized (see that class for why Android needs it). Android
 * builds from 1.16 (versionCode 19) on; older installs do not have this plugin,
 * and the web app checks for it first (frontend lib/voice/background-call.ts).
 *
 * start() must be called while the app is on screen, and only once the
 * microphone is allowed: Android refuses to start a microphone foreground
 * service from the background, and from Android 14 without the permission. Any
 * such refusal comes back as a rejection, which the web app treats as "no
 * background support" and carries on with the call as before.
 */
@CapacitorPlugin(name = "VoiceCallService")
public class VoiceCallServicePlugin extends Plugin {

    @PluginMethod
    public void start(final PluginCall call) {
        Context context = getContext();
        Intent intent = new Intent(context, VoiceCallService.class);
        intent.putExtra(VoiceCallService.EXTRA_TITLE, call.getString("title", ""));
        intent.putExtra(VoiceCallService.EXTRA_TEXT, call.getString("text", ""));
        try {
            ContextCompat.startForegroundService(context, intent);
            call.resolve();
        } catch (Exception e) {
            call.reject("Could not start the call service: " + e.getMessage());
        }
    }

    @PluginMethod
    public void stop(final PluginCall call) {
        getContext().stopService(new Intent(getContext(), VoiceCallService.class));
        call.resolve();
    }

    @Override
    protected void handleOnDestroy() {
        // The activity is going away for good; a service left behind would keep
        // announcing a call that no longer exists.
        getContext().stopService(new Intent(getContext(), VoiceCallService.class));
        super.handleOnDestroy();
    }
}
