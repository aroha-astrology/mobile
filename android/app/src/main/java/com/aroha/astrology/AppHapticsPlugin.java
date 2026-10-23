package com.aroha.astrology;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import com.getcapacitor.JSArray;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import org.json.JSONException;

/**
 * Short vibrations for the Morning Ritual (the seal being held, each jap bead, each breath) and the
 * other small taps in the web layer (frontend lib/haptics.ts). A WebView can call
 * navigator.vibrate on some phones and not on others; this goes to the system vibrator directly, so
 * the feel is the same everywhere. No permission is needed for a vibration of this length except
 * VIBRATE, which is a normal (install-time) permission.
 *
 * The web layer falls back to navigator.vibrate when this plugin is not there (an older build, or
 * the browser), so nothing breaks without it.
 */
@CapacitorPlugin(name = "AppHaptics")
public class AppHapticsPlugin extends Plugin {

    /** Never vibrate longer than this in one pulse: a ritual tap is a tick, not a buzz. */
    private static final long MAX_PULSE_MS = 200;
    /** A pattern is at most this many segments, so a bad caller cannot queue a long buzz. */
    private static final int MAX_SEGMENTS = 12;

    private Vibrator vibrator() {
        Context context = getContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager manager = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            return manager == null ? null : manager.getDefaultVibrator();
        }
        return (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    }

    /** One tick of {@code ms} milliseconds (1 to 200). */
    @PluginMethod
    public void pulse(final PluginCall call) {
        Vibrator v = vibrator();
        if (v == null || !v.hasVibrator()) {
            call.resolve();
            return;
        }
        long ms = Math.max(1, Math.min(MAX_PULSE_MS, call.getInt("ms", 15)));
        vibrate(v, VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
        call.resolve();
    }

    /**
     * A pattern of alternating wait and buzz lengths in milliseconds, starting with a wait, as
     * Vibrator.vibrate takes it. Each length is capped, and so is the number of segments.
     */
    @PluginMethod
    public void pattern(final PluginCall call) {
        Vibrator v = vibrator();
        JSArray raw = call.getArray("pattern");
        if (v == null || !v.hasVibrator() || raw == null || raw.length() == 0) {
            call.resolve();
            return;
        }
        int n = Math.min(raw.length(), MAX_SEGMENTS);
        long[] timings = new long[n];
        try {
            for (int i = 0; i < n; i++) {
                timings[i] = Math.max(0, Math.min(MAX_PULSE_MS, raw.getLong(i)));
            }
        } catch (JSONException e) {
            call.reject("pattern must be an array of numbers");
            return;
        }
        vibrate(v, VibrationEffect.createWaveform(timings, -1));
        call.resolve();
    }

    private void vibrate(Vibrator v, VibrationEffect effect) {
        v.vibrate(effect);
    }
}
