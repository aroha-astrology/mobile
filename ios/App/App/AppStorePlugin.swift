import Foundation
import Capacitor
import StoreKit

/// The Aroha Pass and coin packs on an iPhone, through StoreKit 2 (JS side: frontend lib/app-store.ts).
///
/// Apple's rule (App Review 3.1.1) is that digital goods inside the iOS app are sold through the
/// App Store. The web app calls this plugin; the server never trusts what comes back until it has
/// checked Apple's own signature on the JWS (backend billing/app-store-verify.ts).
///
/// Every purchase carries `appAccountToken` = the user's id, which Apple echoes in the signed
/// transaction and in the App Store Server Notifications, so the server knows whose it is.
///
/// NOT compiled or run: there is no Xcode/macOS on the dev machine. Needs a Codemagic build, the
/// In-App Purchase capability on the App ID, and the products created in App Store Connect:
///   subscriptions (one group): aroha_pass_silver, aroha_pass_gold, aroha_pass_platinum
///   consumables: coins_100, coins_330, coins_700, coins_1800
@objc(AppStorePlugin)
public class AppStorePlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "AppStorePlugin"
    public let jsName = "AppStore"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "purchase", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "currentSubscriptions", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "unfinished", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "finish", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "prices", returnType: CAPPluginReturnPromise),
    ]

    @objc func purchase(_ call: CAPPluginCall) {
        guard #available(iOS 15.0, *) else { return call.reject("StoreKit 2 needs iOS 15", "unsupported") }
        guard let productId = call.getString("productId"),
              let token = call.getString("appAccountToken"),
              let account = UUID(uuidString: token)
        else { return call.reject("productId and a UUID appAccountToken are required", "bad_request") }
        Task {
            do {
                guard let product = try await Product.products(for: [productId]).first else {
                    return call.reject("Product not found in the App Store", "not_found")
                }
                let result = try await product.purchase(options: [.appAccountToken(account)])
                switch result {
                case .success(let verification):
                    guard case .verified(let transaction) = verification else {
                        return call.reject("The App Store could not verify this purchase", "unverified")
                    }
                    call.resolve([
                        "productId": transaction.productID,
                        "signedTransaction": verification.jwsRepresentation,
                        "state": "purchased",
                    ])
                case .pending:
                    call.resolve(["productId": productId, "signedTransaction": "", "state": "pending"])
                case .userCancelled:
                    call.reject("cancelled", "cancelled")
                @unknown default:
                    call.reject("Unknown purchase result", "unknown")
                }
            } catch {
                call.reject(error.localizedDescription, "failed")
            }
        }
    }

    /// The newest signed transaction of each auto-renewable subscription this Apple ID holds now.
    @objc func currentSubscriptions(_ call: CAPPluginCall) {
        guard #available(iOS 15.0, *) else { return call.resolve(["purchases": []]) }
        Task {
            var out: [[String: String]] = []
            for await entitlement in Transaction.currentEntitlements {
                if case .verified(let tx) = entitlement, tx.productType == .autoRenewable {
                    out.append(["productId": tx.productID, "signedTransaction": entitlement.jwsRepresentation])
                }
            }
            call.resolve(["purchases": out])
        }
    }

    /// Paid but not yet finished purchases (the app was killed between paying and telling the server).
    @objc func unfinished(_ call: CAPPluginCall) {
        guard #available(iOS 15.0, *) else { return call.resolve(["purchases": []]) }
        Task {
            var out: [[String: String]] = []
            for await result in Transaction.unfinished {
                if case .verified(let tx) = result {
                    out.append(["productId": tx.productID, "signedTransaction": result.jwsRepresentation])
                }
            }
            call.resolve(["purchases": out])
        }
    }

    /// Finishes the transaction once the server has recorded it, so StoreKit stops offering it.
    @objc func finish(_ call: CAPPluginCall) {
        guard #available(iOS 15.0, *) else { return call.resolve() }
        guard let signed = call.getString("signedTransaction") else {
            return call.reject("signedTransaction is required", "bad_request")
        }
        Task {
            for await result in Transaction.unfinished {
                if case .verified(let tx) = result, result.jwsRepresentation == signed {
                    await tx.finish()
                    break
                }
            }
            call.resolve()
        }
    }

    @objc func prices(_ call: CAPPluginCall) {
        guard #available(iOS 15.0, *) else { return call.resolve(["prices": []]) }
        let ids = call.getArray("productIds", String.self) ?? []
        Task {
            do {
                let products = try await Product.products(for: ids)
                call.resolve([
                    "prices": products.map { ["productId": $0.id, "displayPrice": $0.displayPrice] },
                ])
            } catch {
                call.reject(error.localizedDescription, "failed")
            }
        }
    }
}
