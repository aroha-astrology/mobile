package com.aroha.astrology;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;

/**
 * The foreground service that lets a voice call keep running while the app is
 * minimized.
 *
 * Android silences the microphone of an app that is not on screen, however alive
 * its WebView is, unless the app is running a foreground service of type
 * "microphone" and showing its notification. The call itself is not in here: the
 * audio, the socket and the minute renewals are all in the web app inside the
 * WebView (frontend lib/voice/gemini-live-client.ts). This service does nothing
 * but exist, so that Android keeps the app's process, and its microphone, alive.
 *
 * It is started by VoiceCallServicePlugin when a call begins and stopped when it
 * ends. It is deliberately NOT sticky: if Android kills the app, the call is gone
 * with it, and restarting an empty service would leave a notification claiming a
 * call that does not exist. Swiping the app away from recents also ends it.
 */
public class VoiceCallService extends Service {

    static final String EXTRA_TITLE = "title";
    static final String EXTRA_TEXT = "text";

    private static final String CHANNEL_ID = "voice_call";
    private static final int NOTIFICATION_ID = 7421;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String title = intent != null ? intent.getStringExtra(EXTRA_TITLE) : null;
        String text = intent != null ? intent.getStringExtra(EXTRA_TEXT) : null;
        Notification notification = buildNotification(
            title != null ? title : getString(R.string.app_name),
            text != null ? text : ""
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // The type must match the manifest entry, and from Android 14 starting
            // it needs the RECORD_AUDIO permission to be granted already (the web app
            // starts this after the microphone prompt, never before).
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        return START_NOT_STICKY;
    }

    private Notification buildNotification(String title, String text) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null && manager.getNotificationChannel(CHANNEL_ID) == null) {
                // LOW: a call in progress is not something to ping or buzz about.
                NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.voice_call_channel),
                    NotificationManager.IMPORTANCE_LOW
                );
                channel.setShowBadge(false);
                manager.createNotificationChannel(channel);
            }
        }

        // Tapping it brings the app, and the call screen, back.
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent tap = PendingIntent.getActivity(
            this,
            0,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(tap)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // The app was swiped away: the WebView, and with it the call, is gone.
        stopSelf();
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
