package com.aroha.astrology;

import android.app.Activity;
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

@CapacitorPlugin(name = "PlayBilling")
public class PlayBillingPlugin extends Plugin implements PurchasesUpdatedListener {

    private BillingClient billingClient;
    /** The in-flight purchase call, resolved/rejected from onPurchasesUpdated(). */
    private volatile PluginCall pendingPurchaseCall;

    private boolean billingReady = false;
    private boolean connecting = false;
    private final List<ConnectionWaiter> connectionWaiters = new ArrayList<>();

    private interface ConnectionCallback {
        void onReady();
    }

    /** Pairs a waiting call's success/failure continuations while a connection attempt is in flight. */
    private static final class ConnectionWaiter {
        final ConnectionCallback callback;
        final PluginCall failureCall;

        ConnectionWaiter(ConnectionCallback callback, PluginCall failureCall) {
            this.callback = callback;
            this.failureCall = failureCall;
        }
    }

    @Override
    public void load() {
        billingClient = BillingClient.newBuilder(getContext())
            .setListener(this)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build();
        connectToBillingService();
    }

    /**
     * Starts (or continues) connecting to the Play Billing service using a single, plugin-lifetime
     * listener. Play Billing keeps this listener registered across reconnects, so it must never close
     * over a specific PluginCall — only shared state (the waiters queue) is touched here.
     */
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
                    for (ConnectionWaiter waiter : waiters) {
                        waiter.callback.onReady();
                    }
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
        if (pendingPurchaseCall != null) {
            call.reject("A purchase is already in progress");
            return;
        }
        final String productId = call.getString("productId");
        if (productId == null || productId.isEmpty()) {
            call.reject("productId is required");
            return;
        }
        final Activity activity = getActivity();
        if (activity == null) {
            call.reject("No current activity");
            return;
        }

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
                    call.reject("Failed to load product: " + result.getDebugMessage());
                    return;
                }
                List<ProductDetails> productDetailsList = productDetailsResult.getProductDetailsList();
                if (productDetailsList.isEmpty()) {
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

                pendingPurchaseCall = call;
                BillingResult launchResult = billingClient.launchBillingFlow(activity, flowParams);
                if (launchResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                    pendingPurchaseCall = null;
                    call.reject(
                        "Failed to launch purchase: " + launchResult.getDebugMessage(),
                        String.valueOf(launchResult.getResponseCode())
                    );
                }
            });
        }, call);
    }

    @Override
    public void onPurchasesUpdated(@NonNull BillingResult result, List<Purchase> purchases) {
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
        ensureConnected(() -> {
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
        }, call);
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
