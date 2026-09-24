package com.aroha.astrology;

import android.os.RemoteException;
import com.android.installreferrer.api.InstallReferrerClient;
import com.android.installreferrer.api.InstallReferrerStateListener;
import com.android.installreferrer.api.ReferrerDetails;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * Play Install Referrer: the `referrer=` query a Play Store link carried when
 * this install happened. The web layer's referral share links put the sharer's
 * code in it (frontend lib/referral.ts referralPlayStoreUrl), and
 * lib/install-referrer.ts reads it back here on first launch so onboarding can
 * pre-fill the code — before this, a friend who installed from a shared link
 * had to type the code by hand or the referrer got no credit.
 *
 * Always resolves (never rejects) — `referrer` is null when Play has nothing
 * (sideloaded build, no referrer, service unavailable). The caller only
 * needs "a code or not".
 */
@CapacitorPlugin(name = "InstallReferrer")
public class InstallReferrerPlugin extends Plugin {

    @PluginMethod
    public void getReferrer(final PluginCall call) {
        final InstallReferrerClient client = InstallReferrerClient.newBuilder(getContext()).build();
        final boolean[] settled = { false };
        try {
            client.startConnection(
                new InstallReferrerStateListener() {
                    @Override
                    public void onInstallReferrerSetupFinished(int responseCode) {
                        JSObject result = new JSObject();
                        result.put("referrer", (Object) null);
                        if (responseCode == InstallReferrerClient.InstallReferrerResponse.OK) {
                            try {
                                ReferrerDetails details = client.getInstallReferrer();
                                result.put("referrer", details.getInstallReferrer());
                                result.put("installBeginTimestampSeconds", details.getInstallBeginTimestampSeconds());
                            } catch (RemoteException ignored) {
                                // leave referrer null
                            }
                        }
                        settle(call, client, settled, result);
                    }

                    @Override
                    public void onInstallReferrerServiceDisconnected() {
                        JSObject result = new JSObject();
                        result.put("referrer", (Object) null);
                        settle(call, client, settled, result);
                    }
                }
            );
        } catch (RuntimeException e) {
            JSObject result = new JSObject();
            result.put("referrer", (Object) null);
            settle(call, client, settled, result);
        }
    }

    private static void settle(PluginCall call, InstallReferrerClient client, boolean[] settled, JSObject result) {
        synchronized (settled) {
            if (settled[0]) return;
            settled[0] = true;
        }
        call.resolve(result);
        try {
            client.endConnection();
        } catch (RuntimeException ignored) {
            // already disconnected
        }
    }
}
