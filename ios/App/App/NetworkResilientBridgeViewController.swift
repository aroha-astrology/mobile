import UIKit
import WebKit
import Capacitor

/// Stock `CAPBridgeViewController` has no handling for a failed initial page
/// load. This app's `capacitor.config.ts` always points `server.url` at the
/// live production site, so a cold-start network hiccup (cellular reconnect,
/// DNS blip, slow TLS handshake — much rarer on a dev machine, common on a
/// real device) leaves WKWebView showing nothing at all: no error, no retry,
/// just its plain background, indistinguishable from a frozen app.
///
/// `capacitor.config.ts` already has a comment describing the intended
/// fallback ("if unreachable, the bundled loading screen shows and
/// redirects") — that screen (`out/index.html`, bundled at
/// `ios/App/App/public/index.html`) already implements offline detection +
/// a manual retry button + auto-redirect back to the live URL once
/// `navigator.onLine` fires. It was just never wired up. This subclass wires
/// it up: on a failed provisional navigation, load that bundled page instead
/// of leaving the WebView blank; everything after that (retry timing,
/// redirect back to the live app) is handled by the existing JS there.
///
/// NOT compiled/tested locally — no Xcode/macOS on this dev machine. Needs a
/// Codemagic build + a real device (or simulator with Network Link
/// Conditioner) test before shipping to TestFlight.
class NetworkResilientBridgeViewController: CAPBridgeViewController {
    private var didFallBackToLocal = false
    private var failureProxy: NavigationFailureProxy?

    override func capacitorDidLoad() {
        super.capacitorDidLoad()
        guard let webView = webView else { return }
        // Capacitor's own navigation delegate handles JS-bridge injection and
        // the custom asset scheme — it must keep receiving every call. We
        // only add the one behavior it's missing, not replace it.
        let originalDelegate = webView.navigationDelegate
        let proxy = NavigationFailureProxy(forwardingTo: originalDelegate) { [weak self] in
            self?.loadBundledFallback()
        }
        failureProxy = proxy
        webView.navigationDelegate = proxy
    }

    private func loadBundledFallback() {
        // Guarded to fire once: if the local page's own redirect back to the
        // live URL also fails (still offline), leave its "Retry"/offline UI
        // on screen rather than reloading over it — its online-listener and
        // manual retry button already cover that case from here.
        guard !didFallBackToLocal,
            let fallbackURL = Bundle.main.url(forResource: "index", withExtension: "html", subdirectory: "public")
        else {
            return
        }
        didFallBackToLocal = true
        webView?.loadFileURL(fallbackURL, allowingReadAccessTo: fallbackURL.deletingLastPathComponent())
    }
}

/// Forwards every `WKNavigationDelegate` call to `target` unchanged, except
/// the two failure callbacks, which additionally invoke `onFailure` first.
/// `NSObject` + `responds(to:)`/`forwardingTarget(for:)` is the standard
/// Cocoa pattern for augmenting an existing delegate without having to
/// reimplement (and risk drifting from) every method it already handles.
private class NavigationFailureProxy: NSObject, WKNavigationDelegate {
    private weak var target: WKNavigationDelegate?
    private let onFailure: () -> Void

    init(forwardingTo target: WKNavigationDelegate?, onFailure: @escaping () -> Void) {
        self.target = target
        self.onFailure = onFailure
    }

    override func responds(to aSelector: Selector!) -> Bool {
        super.responds(to: aSelector) || (target?.responds(to: aSelector) ?? false)
    }

    override func forwardingTarget(for aSelector: Selector!) -> Any? {
        super.responds(to: aSelector) ? nil : target
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        handleFailure(error)
        target?.webView?(webView, didFailProvisionalNavigation: navigation, withError: error)
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        handleFailure(error)
        target?.webView?(webView, didFail: navigation, withError: error)
    }

    private func handleFailure(_ error: Error) {
        // A navigation cancelled because a newer one superseded it (rapid
        // taps, a quick client-side redirect) is not a load failure — only
        // react to errors that mean the page genuinely didn't load.
        let nsError = error as NSError
        if nsError.domain == NSURLErrorDomain && nsError.code == NSURLErrorCancelled {
            return
        }
        onFailure()
    }
}
