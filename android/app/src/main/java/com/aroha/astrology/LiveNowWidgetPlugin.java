package com.aroha.astrology;

import android.content.Context;
import android.content.SharedPreferences;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * Tells the Right now home-screen widget (LiveNowWidgetProvider) where to look. The web layer
 * (frontend lib/live-now-widget.ts) calls setPlace with the place the Now Bar shows and the server
 * address; the widget keeps them in SharedPreferences and fetches /v1/widgets/live-now on its own.
 * clear() forgets the place (sign-out), so a widget left on the home screen shows nothing personal.
 *
 * The place is rounded to two decimals (about a kilometre) before it is stored: enough for a
 * panchang, and no more precise than the server's own cache key.
 */
@CapacitorPlugin(name = "LiveNowWidget")
public class LiveNowWidgetPlugin extends Plugin {

    private SharedPreferences prefs() {
        return getContext().getSharedPreferences(LiveNowWidgetProvider.PREFS, Context.MODE_PRIVATE);
    }

    @PluginMethod
    public void setPlace(final PluginCall call) {
        String apiBase = call.getString("apiBase");
        String tz = call.getString("tz");
        Double lat = call.getDouble("lat");
        Double lon = call.getDouble("lon");
        if (apiBase == null || !apiBase.startsWith("https://") || tz == null || lat == null || lon == null) {
            call.reject("apiBase (https), tz, lat and lon are required");
            return;
        }
        if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
            call.reject("lat or lon out of range");
            return;
        }
        String place = call.getString("place", "");
        prefs()
            .edit()
            .putString(LiveNowWidgetProvider.KEY_API_BASE, apiBase.replaceAll("/+$", ""))
            .putString(LiveNowWidgetProvider.KEY_TZ, tz)
            .putFloat(LiveNowWidgetProvider.KEY_LAT, (float) (Math.round(lat * 100.0) / 100.0))
            .putFloat(LiveNowWidgetProvider.KEY_LON, (float) (Math.round(lon * 100.0) / 100.0))
            .putString(LiveNowWidgetProvider.KEY_PLACE, place == null ? "" : place)
            .apply();
        LiveNowWidgetProvider.refreshAll(getContext());
        call.resolve();
    }

    @PluginMethod
    public void clear(final PluginCall call) {
        prefs().edit().clear().apply();
        LiveNowWidgetProvider.refreshAll(getContext());
        call.resolve();
    }
}
