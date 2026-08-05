package com.aroha.astrology;

import android.app.Activity;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.google.android.play.core.review.ReviewManager;
import com.google.android.play.core.review.ReviewManagerFactory;

/**
 * Google Play In-App Review. Unlike PlayBillingPlugin there is no connection or
 * pending-call state to guard: each request is self-contained and Play Core
 * delivers both listeners on the main thread.
 *
 * Google deliberately reports nothing back — not the star count, not whether a
 * review was submitted, not even whether the card was shown (it silently does
 * nothing once the user is over their quota). So resolve() here means "the flow
 * finished", never "the user reviewed", and no caller may branch on it.
 */
@CapacitorPlugin(name = "AppReview")
public class AppReviewPlugin extends Plugin {

    @PluginMethod
    public void requestReview(final PluginCall call) {
        final Activity activity = getActivity();
        if (activity == null) {
            call.reject("No current activity");
            return;
        }
        final ReviewManager manager = ReviewManagerFactory.create(getContext());
        manager
            .requestReviewFlow()
            .addOnCompleteListener(request -> {
                if (!request.isSuccessful()) {
                    call.reject("Review flow unavailable");
                    return;
                }
                manager.launchReviewFlow(activity, request.getResult()).addOnCompleteListener(ignored -> call.resolve());
            });
    }
}
