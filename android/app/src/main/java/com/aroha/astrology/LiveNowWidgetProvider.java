package com.aroha.astrology;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * The Right now home-screen widget (v7 Sprint 3). It shows what GET /v1/widgets/live-now answers for
 * the place the app last told it about: the hora, the choghadiya, whether Rahu Kaal is on, and the
 * next good window. Android phones have home-screen widgets only (no third-party lock-screen
 * widgets), so that is what this is.
 *
 * The answer is public (no sign-in) and under 2 KB, so the widget needs no account: the app
 * hands it the place and the server address once (LiveNowWidgetPlugin.setPlace, stored in
 * SharedPreferences) and the widget fetches on its own timer. Android refreshes a widget at most
 * every 30 minutes through updatePeriodMillis; the server answer is good for 15, so every refresh
 * is current. Until the app has been opened with the Now Bar switched on there is no place and the
 * widget says to open Aroha. The server answers 404 while its switches are off, and the widget
 * then says it is not available yet.
 *
 * The widget's words are English for now; the app's own screens are fully translated.
 */
public class LiveNowWidgetProvider extends AppWidgetProvider {

    static final String PREFS = "aroha_live_now_widget";
    static final String KEY_API_BASE = "apiBase";
    static final String KEY_LAT = "lat";
    static final String KEY_LON = "lon";
    static final String KEY_TZ = "tz";
    static final String KEY_PLACE = "place";

    private static final int TIMEOUT_MS = 8000;
    /** The answer is under 2 KB; anything far bigger is not from our server. */
    private static final int MAX_BYTES = 16 * 1024;

    @Override
    public void onUpdate(final Context context, final AppWidgetManager manager, final int[] ids) {
        // The fetch needs the network, which the main thread may not use; goAsync keeps the
        // receiver alive until the worker is done.
        final PendingResult pending = goAsync();
        new Thread(
                new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Rendered rendered = fetchAndRender(context);
                            for (int id : ids) {
                                manager.updateAppWidget(id, views(context, rendered));
                            }
                        } finally {
                            pending.finish();
                        }
                    }
                },
                "aroha-live-now-widget")
            .start();
    }

    /** Asks every placed widget to refresh now (the app calls this when the place changes). */
    static void refreshAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, LiveNowWidgetProvider.class));
        if (ids.length == 0) return;
        Intent intent = new Intent(context, LiveNowWidgetProvider.class);
        intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
        context.sendBroadcast(intent);
    }

    /** The four lines a widget shows. */
    static final class Rendered {
        final String title;
        final String line1;
        final String line2;
        final String line3;

        Rendered(String title, String line1, String line2, String line3) {
            this.title = title;
            this.line1 = line1;
            this.line2 = line2;
            this.line3 = line3;
        }
    }

    static Rendered fetchAndRender(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String apiBase = prefs.getString(KEY_API_BASE, null);
        String tz = prefs.getString(KEY_TZ, null);
        if (apiBase == null || tz == null || !prefs.contains(KEY_LAT) || !prefs.contains(KEY_LON)) {
            return new Rendered("Right now", "Open Aroha to set up this widget.", "", "");
        }
        String place = prefs.getString(KEY_PLACE, "");
        try {
            String url =
                apiBase
                    + "/v1/widgets/live-now?lat="
                    + prefs.getFloat(KEY_LAT, 0f)
                    + "&lon="
                    + prefs.getFloat(KEY_LON, 0f)
                    + "&tz="
                    + URLEncoder.encode(tz, "UTF-8");
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("Accept", "application/json");
            try {
                int status = conn.getResponseCode();
                if (status == 404) {
                    return new Rendered("Right now", "Not available yet.", "", "");
                }
                if (status != 200) {
                    return new Rendered("Right now", "Could not refresh. Will try again soon.", "", "");
                }
                return render(parse(conn.getInputStream()), place);
            } finally {
                conn.disconnect();
            }
        } catch (IOException | JSONException e) {
            return new Rendered("Right now", "Could not refresh. Will try again soon.", "", "");
        }
    }

    private static JSONObject parse(InputStream in) throws IOException, JSONException {
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            char[] buf = new char[1024];
            int n;
            while ((n = reader.read(buf)) > 0) {
                out.append(buf, 0, n);
                if (out.length() > MAX_BYTES) throw new IOException("answer too large");
            }
        }
        return new JSONObject(out.toString());
    }

    /** Turns the server's answer into the widget's lines. Pure, so it can be tested without a phone. */
    static Rendered render(JSONObject live, String place) throws JSONException {
        String title = place == null || place.isEmpty() ? "Right now" : "Right now · " + place;

        JSONObject rahu = live.getJSONObject("rahuKaal");
        JSONObject abhijit = live.getJSONObject("abhijit");
        JSONObject chog = live.optJSONObject("choghadiya");
        JSONObject hora = live.optJSONObject("hora");
        JSONObject nextGood = live.optJSONObject("nextGood");
        JSONObject nextCaution = live.optJSONObject("nextCaution");

        String line1;
        if (rahu.optBoolean("on")) {
            line1 = "Rahu Kaal until " + rahu.getString("end");
        } else if (abhijit.optBoolean("on")) {
            line1 = "Abhijit Muhurta until " + abhijit.getString("end");
        } else if (chog != null) {
            line1 = chog.getString("name") + " choghadiya until " + chog.getString("end");
        } else {
            line1 = live.getString("vara");
        }

        String line2 = hora == null ? "" : "Hora of " + hora.getString("planet") + " until " + hora.getString("end");

        String line3;
        if (nextGood != null) {
            line3 = "Next good: " + nextGood.getString("label") + " at " + nextGood.getString("start");
        } else if (nextCaution != null) {
            line3 = nextCaution.getString("label") + " at " + nextCaution.getString("start");
        } else {
            line3 = "";
        }
        return new Rendered(title, line1, line2, line3);
    }

    private static RemoteViews views(Context context, Rendered rendered) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_live_now);
        views.setTextViewText(R.id.widget_title, rendered.title);
        views.setTextViewText(R.id.widget_line1, rendered.line1);
        views.setTextViewText(R.id.widget_line2, rendered.line2);
        views.setTextViewText(R.id.widget_line3, rendered.line3);
        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent tap =
            PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root, tap);
        return views;
    }
}
