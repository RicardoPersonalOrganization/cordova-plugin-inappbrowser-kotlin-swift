import Foundation
import WebKit

public let IAB_BRIDGE_NAME = "cordova_iab"

@objc public protocol InAppBrowserNavigationDelegate: AnyObject {
    func browserExit()
    func isBeforeloadEnabled() -> Bool
    func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void)
    func didStartProvisionalNavigation(_ webView: WKWebView)
    func didFinishNavigation(_ webView: WKWebView)
    func didFailNavigation(_ webView: WKWebView, withError error: Error)
    func userContentController(_ userContentController: WKUserContentController, didReceiveScriptMessage message: WKScriptMessage)
}
