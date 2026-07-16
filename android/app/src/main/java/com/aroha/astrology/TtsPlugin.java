package com.aroha.astrology;

import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import androidx.annotation.NonNull;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.util.Locale;
import java.util.UUID;

/**
 * Wraps Android's native android.speech.tts.TextToSpeech engine — added
 * because the JS Web Speech API (window.speechSynthesis) is unreliable
 * inside Capacitor's Android WebView (a long-standing open Chromium bug),
 * so the frontend's speaker icon was silently hiding itself on real devices.
 * This talks to the OS TTS engine directly instead, bypassing the WebView
 * gap entirely. Mirrors the TtsBackend interface in the frontend's
 * lib/tts.ts so the JS side is a drop-in swap, same pattern as
 * PlayBillingPlugin for Play Billing.
 *
 * All TextToSpeech callbacks (init, utterance progress) land on their own
 * internal thread, not necessarily Capacitor's plugin-call thread — the
 * mutable state below (ready, pendingSpeakCall) is only ever touched inside
 * those callbacks or PluginMethod bodies, and TextToSpeech itself is
 * documented as safe to call from multiple threads, so no extra locking is
 * needed beyond not racing a resolve/reject on the same PluginCall twice
 * (guarded by nulling pendingSpeakCall before resolving it).
 */
@CapacitorPlugin(name = "Tts")
public class TtsPlugin extends Plugin {

    private TextToSpeech tts;
    private volatile boolean ready = false;
    private PluginCall pendingSpeakCall;

    @Override
    public void load() {
        tts = new TextToSpeech(getContext(), status -> ready = status == TextToSpeech.SUCCESS);
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {}

            @Override
            public void onDone(String utteranceId) {
                resolvePendingSpeak();
            }

            @Override
            public void onError(String utteranceId) {
                resolvePendingSpeak();
            }
        });
    }

    private synchronized void resolvePendingSpeak() {
        PluginCall call = pendingSpeakCall;
        pendingSpeakCall = null;
        if (call != null) call.resolve();
    }

    @PluginMethod
    public void isLanguageAvailable(PluginCall call) {
        String lang = call.getString("lang");
        JSObject ret = new JSObject();
        if (!ready || lang == null || lang.isEmpty()) {
            ret.put("available", false);
            call.resolve(ret);
            return;
        }
        int result = tts.isLanguageAvailable(Locale.forLanguageTag(lang));
        boolean available = result == TextToSpeech.LANG_AVAILABLE
            || result == TextToSpeech.LANG_COUNTRY_AVAILABLE
            || result == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE;
        ret.put("available", available);
        call.resolve(ret);
    }

    @PluginMethod
    public synchronized void speak(@NonNull PluginCall call) {
        String text = call.getString("text");
        String lang = call.getString("lang");
        if (text == null || text.isEmpty()) {
            call.reject("text is required");
            return;
        }
        if (!ready) {
            call.reject("TTS engine not ready");
            return;
        }
        if (lang != null && !lang.isEmpty()) {
            tts.setLanguage(Locale.forLanguageTag(lang));
        }
        // Only one utterance is ever tracked — a new speak() supersedes any
        // pending one, matching the JS side's single "speakingId" model. The
        // superseded call resolves (not rejects) since being interrupted by
        // the user tapping a different message isn't an error.
        if (pendingSpeakCall != null) {
            PluginCall previous = pendingSpeakCall;
            pendingSpeakCall = null;
            previous.resolve();
        }
        pendingSpeakCall = call;

        String utteranceId = UUID.randomUUID().toString();
        Bundle params = new Bundle();
        int result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId);
        if (result != TextToSpeech.SUCCESS) {
            pendingSpeakCall = null;
            call.reject("Failed to start speech");
        }
    }

    @PluginMethod
    public void stop(PluginCall call) {
        if (tts != null) tts.stop();
        resolvePendingSpeak();
        call.resolve();
    }

    @Override
    protected void handleOnDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.handleOnDestroy();
    }
}
