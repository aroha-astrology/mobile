package com.aroha.astrology;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * All mutable state on this plugin (pendingPurchaseCall, connectionWaiters,
 * billingReady, connecting) is only ever read or written on the Android main
 * thread via mainHandler — the same thread Play Billing Library delivers
 * every callback on. Capacitor dispatches @PluginMethod calls on its own
 * background thread, so every method that touches this state hops onto the
 * main thread first. This is plain single-thread confinement instead of
 * locks.
 */
@CapacitorPlugin(name = "PlayBilling")
public class PlayBillingPlugin extends Plugin implements PurchasesUpdatedListener {

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private BillingClient billingClient;
    /** Main-thread only. */
    private PluginCall pendingPurchaseCall;
    /** Main-thread only. */
    private boolean billingReady = false;
    /** Main-thread only. */
    private boolean connecting = false;

    private interface ConnectionCallback {
        void onReady();
    }

    private static final class ConnectionWaiter {
        final ConnectionCallback callback;
        final PluginCall failureCall;

        ConnectionWaiter(ConnectionCallback callback, PluginCall failureCall) {
            this.callback = callback;
            this.failureCall = failureCall;
        }
    }

    /** Main-thread only. */
    private final List<ConnectionWaiter> connectionWaiters = new ArrayList<>();

    @Override
    public void load() {
        billingClient = BillingClient.newBuilder(getContext())
            .setListener(this)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build();
        mainHandler.post(this::connectToBillingService);
    }

    /** Must only be called on the main thread. */
    private void connectToBillingService() {
        if (connecting) return;
        connecting = true;
        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult result) {
                connecting = false;
                List<ConnectionWaiter> waiters = new ArrayList<>(connectionWaiters);
                connectionWaiters.clear();
                if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    billingReady = true;
                    for (ConnectionWaiter waiter : waiters) waiter.callback.onReady();
                } else {
                    billingReady = false;
                    for (ConnectionWaiter waiter : waiters) {
                        waiter.failureCall.reject("Billing unavailable: " + result.getDebugMessage());
                    }
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                connecting = false;
                billingReady = false;
                if (pendingPurchaseCall != null) {
                    PluginCall call = pendingPurchaseCall;
                    pendingPurchaseCall = null;
                    call.reject("Billing service disconnected");
                }
            }
        });
    }

    /** Must only be called on the main thread. */
    private void ensureConnected(ConnectionCallback callback, PluginCall failureCall) {
        if (billingReady && billingClient.isReady()) {
            callback.onReady();
            return;
        }
        connectionWaiters.add(new ConnectionWaiter(callback, failureCall));
        connectToBillingService();
    }

    @PluginMethod
    public void purchaseProduct(final PluginCall call) {
        final Activity activity = getActivity();
        if (activity == null) {
            call.reject("No current activity");
            return;
        }
        mainHandler.post(() -> {
            if (pendingPurchaseCall != null) {
                call.reject("A purchase is already in progress");
                return;
            }
            final String productId = call.getString("productId");
            if (productId == null || productId.isEmpty()) {
                call.reject("productId is required");
                return;
            }
            // Claim the slot immediately, atomically with the guard check above
            // (both run in this same Runnable on the main thread) — so a second
            // purchaseProduct call sees this one as in-flight even while product
            // details are still loading, instead of only being caught after the
            // fact deep inside the async chain.
            pendingPurchaseCall = call;

            ensureConnected(() -> {
                QueryProductDetailsParams.Product product = QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(productId)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build();
                QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                    .setProductList(Collections.singletonList(product))
                    .build();

                billingClient.queryProductDetailsAsync(params, (result, productDetailsResult) -> {
                    if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                        pendingPurchaseCall = null;
                        call.reject("Failed to load product: " + result.getDebugMessage());
                        return;
                    }
                    List<ProductDetails> productDetailsList = productDetailsResult.getProductDetailsList();
                    if (productDetailsList.isEmpty()) {
                        pendingPurchaseCall = null;
                        call.reject("Unknown product: " + productId);
                        return;
                    }
                    ProductDetails details = productDetailsList.get(0);

                    BillingFlowParams.ProductDetailsParams productDetailsParams =
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(details)
                            .build();
                    BillingFlowParams flowParams = BillingFlowParams.newBuilder()
                        .setProductDetailsParamsList(Collections.singletonList(productDetailsParams))
                        .build();

                    BillingResult launchResult = billingClient.launchBillingFlow(activity, flowParams);
                    if (launchResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                        pendingPurchaseCall = null;
                        call.reject(
                            "Failed to launch purchase: " + launchResult.getDebugMessage(),
                            String.valueOf(launchResult.getResponseCode())
                        );
                    }
                    // else: leave pendingPurchaseCall set — onPurchasesUpdated settles it.
                });
            }, call);
        });
    }

    @Override
    public void onPurchasesUpdated(@NonNull BillingResult result, List<Purchase> purchases) {
        // Play Billing Library invokes this on the same thread the client was
        // built/connected on (the main thread, via mainHandler above) — already
        // safe to touch pendingPurchaseCall without an extra hop.
        PluginCall call = pendingPurchaseCall;
        pendingPurchaseCall = null;
        if (call == null) return;

        if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
            call.reject("Purchase failed: " + result.getDebugMessage(), String.valueOf(result.getResponseCode()));
            return;
        }
        if (purchases == null || purchases.isEmpty()) {
            call.reject("Purchase completed with no purchase data");
            return;
        }
        call.resolve(purchaseToJSObject(purchases.get(0)));
    }

    @PluginMethod
    public void queryUnconsumedPurchases(final PluginCall call) {
        mainHandler.post(() -> ensureConnected(() -> {
            QueryPurchasesParams params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build();
            billingClient.queryPurchasesAsync(params, (result, purchases) -> {
                if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                    call.reject("Failed to query purchases: " + result.getDebugMessage());
                    return;
                }
                JSArray array = new JSArray();
                for (Purchase purchase : purchases) {
                    if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                        array.put(purchaseToJSObject(purchase));
                    }
                }
                JSObject ret = new JSObject();
                ret.put("purchases", array);
                call.resolve(ret);
            });
        }, call));
    }

    private JSObject purchaseToJSObject(Purchase purchase) {
        JSObject obj = new JSObject();
        List<String> products = purchase.getProducts();
        obj.put("productId", products.isEmpty() ? "" : products.get(0));
        obj.put("purchaseToken", purchase.getPurchaseToken());
        obj.put("orderId", purchase.getOrderId() == null ? "" : purchase.getOrderId());
        return obj;
    }
}
