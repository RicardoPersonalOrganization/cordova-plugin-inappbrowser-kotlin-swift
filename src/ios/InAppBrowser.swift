import Foundation
import Cordova
import WebKit

@objc(InAppBrowser) class InAppBrowser : CDVPlugin {
    var inAppBrowserViewController: InAppBrowserViewController?
    var callbackId: String?
    var beforeload: String = ""
    var waitForBeforeload: Bool = false

    override func pluginInitialize() {
        beforeload = ""
        waitForBeforeload = false
    }

    override func onReset() {
        // Called when the webview navigates/refreshes - close the IAB if open
        self.close(nil)
    }

    @objc(open:)
    func open(command: CDVInvokedUrlCommand) {
        let urlStr = command.argument(at: 0) as? String
        let target = command.argument(at: 1) as? String ?? "_self"
        let options = command.argument(at: 2) as? String ?? ""

        let pluginResult: CDVPluginResult
        if let urlStr = urlStr, let absoluteUrl = URL(string: urlStr, relativeTo: self.webViewEngine?.url) {
            var resolvedTarget = target
            if isSystemUrl(absoluteUrl) {
                resolvedTarget = "_system"
            }

            if resolvedTarget == "_self" {
                openInCordovaWebView(absoluteUrl, withOptions: options)
            } else if resolvedTarget == "_system" {
                openInSystem(absoluteUrl)
            } else {
                self.callbackId = command.callbackId
                openInInAppBrowser(absoluteUrl, withOptions: options)
            }
            pluginResult = CDVPluginResult(status: CDVCommandStatus_OK)
        } else {
            pluginResult = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: "incorrect number of arguments")
        }
        pluginResult.setKeepCallbackAs(true)
        self.commandDelegate.send(pluginResult, callbackId: command.callbackId)
    }

    func isSystemUrl(_ url: URL) -> Bool {
        if let host = url.host?.lowercased(), host == "itunes.apple.com" {
            return true
        }
        return false
    }

    func openInInAppBrowser(_ url: URL, withOptions options: String) {
        let browserOptions = InAppBrowserOptions.parseOptions(options)

        if inAppBrowserViewController == nil {
            inAppBrowserViewController = InAppBrowserViewController(browserOptions: browserOptions, settings: self.commandDelegate.settings ?? [:])
            inAppBrowserViewController?.navigationDelegate = self
        }

        inAppBrowserViewController?.showLocationBar(browserOptions.location)
        inAppBrowserViewController?.showToolBar(browserOptions.toolbar, atPosition: browserOptions.toolbarposition)
        if browserOptions.closebuttoncaption != nil || browserOptions.closebuttoncolor != nil {
            // index calculation omitted
            inAppBrowserViewController?.setCloseButtonTitle(browserOptions.closebuttoncaption, withColor: browserOptions.closebuttoncolor, atIndex: 0)
        }

        if browserOptions.beforeload != "" {
            beforeload = browserOptions.beforeload
        } else {
            beforeload = "yes"
        }
        waitForBeforeload = beforeload != ""

        // Set Presentation Style
        var presentationStyle: UIModalPresentationStyle = .fullScreen
        if browserOptions.presentationstyle != "" {
            let ps = browserOptions.presentationstyle.lowercased()
            if ps == "pagesheet" {
                presentationStyle = .pageSheet
            } else if ps == "formsheet" {
                presentationStyle = .formSheet
            }
        }
        inAppBrowserViewController?.modalPresentationStyle = presentationStyle

        // Set Transition Style
        var transitionStyle: UIModalTransitionStyle = .coverVertical
        if browserOptions.transitionstyle != nil {
            let ts = browserOptions.transitionstyle!.lowercased()
            if ts == "fliphorizontal" {
                transitionStyle = .flipHorizontal
            } else if ts == "crossdissolve" {
                transitionStyle = .crossDissolve
            }
        }
        inAppBrowserViewController?.modalTransitionStyle = transitionStyle

        // Prevent WebView from bouncing if requested
        if browserOptions.disallowoverscroll {
            inAppBrowserViewController?.webView.scrollView.bounces = false
        }

        // Delay the initial navigation until requested clearing operations are complete.
        clearWebsiteDataByOptions(browserOptions) {
            self.inAppBrowserViewController?.navigateTo(url)
            if !browserOptions.hidden {
                self.show(nil)
            }
        }
    }

    // MARK: - Navigation Delegate
}

extension InAppBrowser: InAppBrowserNavigationDelegate {
    func browserExit() {
        if let cb = callbackId {
            let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: ["type":"exit"] as [String:Any])
            pluginResult?.setKeepCallbackAs(false)
            self.commandDelegate.send(pluginResult, callbackId: cb)
        }
        // Cleanup
        if let vc = inAppBrowserViewController {
            vc.configuration.userContentController.removeScriptMessageHandler(forName: IAB_BRIDGE_NAME)
            vc.webView.stopLoading()
            vc.webView.navigationDelegate = nil
            vc.webView.uiDelegate = nil
            vc.webView.removeFromSuperview()
        }
        inAppBrowserViewController = nil
    }

    func isBeforeloadEnabled() -> Bool { return beforeload != "" }

    func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        // Handle custom schemes and beforeload
        if let url = navigationAction.request.url, let scheme = url.scheme {
            if isAllowedScheme(scheme) {
                UIApplication.shared.open(url, options: [:], completionHandler: nil)
                decisionHandler(.cancel)
                return
            }
        }

        // beforeload handling: if enabled, send event and cancel
        if beforeload != "", waitForBeforeload {
            if let cb = callbackId {
                let message: [String:Any] = ["type":"beforeload","url": navigationAction.request.url?.absoluteString ?? ""]
                let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: message)
                pluginResult?.setKeepCallbackAs(true)
                self.commandDelegate.send(pluginResult, callbackId: cb)
            }
            decisionHandler(.cancel)
            return
        }

        decisionHandler(.allow)
    }

    func didStartProvisionalNavigation(_ webView: WKWebView) {
        if let cb = callbackId {
            let message: [String:Any] = ["type":"loadstart","url": webView.url?.absoluteString ?? ""]
            let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: message)
            pluginResult?.setKeepCallbackAs(true)
            self.commandDelegate.send(pluginResult, callbackId: cb)
        }
    }

    func didFinishNavigation(_ webView: WKWebView) {
        if let cb = callbackId {
            let message: [String:Any] = ["type":"loadstop","url": webView.url?.absoluteString ?? ""]
            let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: message)
            pluginResult?.setKeepCallbackAs(true)
            self.commandDelegate.send(pluginResult, callbackId: cb)
        }
    }

    func didFailNavigation(_ webView: WKWebView, withError error: Error) {
        if let cb = callbackId {
            let message: [String:Any] = ["type":"loaderror","url": webView.url?.absoluteString ?? "", "code": (error as NSError).code, "message": error.localizedDescription] as [String : Any]
            let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: message)
            pluginResult?.setKeepCallbackAs(true)
            self.commandDelegate.send(pluginResult, callbackId: cb)
        }
    }

    func userContentController(_ userContentController: WKUserContentController, didReceiveScriptMessage message: WKScriptMessage) {
        if let cb = callbackId {
            var body: Any = message.body
            if !(body is String) {
                body = String(describing: body)
            }
            let msg: [String:Any] = ["type":"message","data": body]
            let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: msg)
            pluginResult?.setKeepCallbackAs(true)
            self.commandDelegate.send(pluginResult, callbackId: cb)
        }
    }

    func clearWebsiteDataByOptions(_ browserOptions: InAppBrowserOptions, completionHandler: @escaping ()->Void) {
        let dataStore = WKWebsiteDataStore.default()
        if browserOptions.cleardata {
            clearData(dataStore, completionHandler: completionHandler)
            return
        }
        if browserOptions.clearcache {
            clearCookie(dataStore, sessionOnly: false, completionHandler: completionHandler)
            return
        }
        if browserOptions.clearsessioncache {
            clearCookie(dataStore, sessionOnly: true, completionHandler: completionHandler)
            return
        }
        completionHandler()
    }

    func clearData(_ dataStore: WKWebsiteDataStore, completionHandler: @escaping ()->Void) {
        dataStore.fetchDataRecords(ofTypes: WKWebsiteDataStore.allWebsiteDataTypes()) { records in
            dataStore.removeData(ofTypes: WKWebsiteDataStore.allWebsiteDataTypes(), for: records) {
                completionHandler()
            }
        }
    }

    func clearCookie(_ dataStore: WKWebsiteDataStore, sessionOnly: Bool, completionHandler: @escaping ()->Void) {
        let cookieStore = dataStore.httpCookieStore
        cookieStore.getAllCookies { cookies in
            var cookiesToDelete: [HTTPCookie] = []
            for cookie in cookies {
                if !sessionOnly || cookie.isSessionOnly {
                    cookiesToDelete.append(cookie)
                }
            }
            if cookiesToDelete.count == 0 {
                completionHandler()
                return
            }
            var pending = cookiesToDelete.count
            for cookie in cookiesToDelete {
                cookieStore.delete(cookie) {
                    pending -= 1
                    if pending == 0 {
                        completionHandler()
                    }
                }
            }
        }
    }

    @objc(show:)
    func show(_ command: CDVInvokedUrlCommand?) {
        show(command, withNoAnimate:false)
    }

    @objc(show:withNoAnimate:)
    func show(_ command: CDVInvokedUrlCommand?, withNoAnimate noAnimate: Bool) {
        guard let vc = inAppBrowserViewController else { return }
        let nav = UINavigationController(rootViewController: vc)
        nav.isNavigationBarHidden = true
        // Preserve modal presentation style and set presentation delegate for interactive dismissals
        nav.modalPresentationStyle = vc.modalPresentationStyle
        nav.presentationController?.delegate = vc
        DispatchQueue.main.async {
            UIApplication.shared.keyWindow?.rootViewController?.present(nav, animated: !noAnimate, completion: nil)
        }
    }

    @objc(hide:)
    func hide(_ command: CDVInvokedUrlCommand?) {
        DispatchQueue.main.async {
            self.inAppBrowserViewController?.presentingViewController?.dismiss(animated: true, completion: nil)
        }
    }

    @objc(close:)
    func close(_ command: CDVInvokedUrlCommand?) {
        if inAppBrowserViewController == nil {
            NSLog("IAB.close() called but it was already closed.")
            return
        }

        inAppBrowserViewController?.close()
    }

    func openInCordovaWebView(_ url: URL, withOptions options: String) {
        let req = URLRequest(url: url)
        self.webViewEngine?.load(req)
    }

    func openInSystem(_ url: URL) {
        UIApplication.shared.open(url, options: [:], completionHandler: nil)
    }

    @objc(loadAfterBeforeload:)
    func loadAfterBeforeload(_ command: CDVInvokedUrlCommand) {
        let urlStr = command.argument(at: 0) as? String
        if beforeload == "" { NSLog("unexpected loadAfterBeforeload called without feature beforeload=get|post") }
        guard let urlStr = urlStr, let url = URL(string: urlStr) else { return }
        waitForBeforeload = false
        inAppBrowserViewController?.navigateTo(url)
    }

    func isBeforeloadEnabled() -> Bool { return beforeload != "" }

    // Inject helpers
    func injectDeferredObject(_ source: String, withWrapper jsWrapper: String?) {
        // simplified: evaluate on webview
        if let wrapper = jsWrapper {
            let jsonData = try? JSONSerialization.data(withJSONObject: [source])
            if let jsonData = jsonData, let jsonString = String(data: jsonData, encoding: .utf8) {
                let sourceString = String(jsonString.dropFirst().dropLast())
                let jsToInject = String(format: wrapper, sourceString)
                evaluateJavaScript(jsToInject)
            }
        } else {
            evaluateJavaScript(source)
        }
    }

    func evaluateJavaScript(_ script: String) {
        DispatchQueue.main.async {
            self.inAppBrowserViewController?.webView.evaluateJavaScript(script, completionHandler: { (result, error) in
                if let error = error {
                    NSLog("evaluateJavaScript error: \(error.localizedDescription)")
                }
            })
        }
    }

    @objc(injectScriptCode:)
    func injectScriptCode(_ command: CDVInvokedUrlCommand) {
        var jsWrapper: String? = nil
        if let cb = command.callbackId, cb != "INVALID" {
            jsWrapper = "_cdvMessageHandler('\(cb)',JSON.stringify([eval(%@)]));"
        }
        injectDeferredObject(command.argument(at: 0) as? String ?? "", withWrapper: jsWrapper)
    }

    @objc(injectScriptFile:)
    func injectScriptFile(_ command: CDVInvokedUrlCommand) {
        var jsWrapper: String
        if let cb = command.callbackId, cb != "INVALID" {
            jsWrapper = "(function(d) { var c = d.createElement('script'); c.src = %@; c.onload = function() { _cdvMessageHandler('\(cb)'); }; d.body.appendChild(c); })(document)"
        } else {
            jsWrapper = "(function(d) { var c = d.createElement('script'); c.src = %@; d.body.appendChild(c); })(document)"
        }
        injectDeferredObject(command.argument(at: 0) as? String ?? "", withWrapper: jsWrapper)
    }

    @objc(injectStyleCode:)
    func injectStyleCode(_ command: CDVInvokedUrlCommand) {
        var jsWrapper: String
        if let cb = command.callbackId, cb != "INVALID" {
            jsWrapper = "(function(d) { var c = d.createElement('style'); c.innerHTML = %@; d.body.appendChild(c); _cdvMessageHandler('\(cb)'); })(document)"
        } else {
            jsWrapper = "(function(d) { var c = d.createElement('style'); c.innerHTML = %@; d.body.appendChild(c); })(document)"
        }
        injectDeferredObject(command.argument(at: 0) as? String ?? "", withWrapper: jsWrapper)
    }

    @objc(injectStyleFile:)
    func injectStyleFile(_ command: CDVInvokedUrlCommand) {
        var jsWrapper: String
        if let cb = command.callbackId, cb != "INVALID" {
            jsWrapper = "(function(d) { var c = d.createElement('link'); c.rel='stylesheet'; c.type='text/css'; c.href = %@; c.onload = function() { _cdvMessageHandler('\(cb)'); }; d.body.appendChild(c); })(document)"
        } else {
            jsWrapper = "(function(d) { var c = d.createElement('link'); c.rel='stylesheet'; c.type='text/css'; c.href = %@; d.body.appendChild(c); })(document)"
        }
        injectDeferredObject(command.argument(at: 0) as? String ?? "", withWrapper: jsWrapper)
    }

    func isAllowedScheme(_ scheme: String) -> Bool {
        let allowed = self.commandDelegate.settings?["AllowedSchemes"] as? String
        if allowed == nil || allowed == "" { return false }
        for s in allowed!.split(separator: ",") {
            if String(s) == scheme { return true }
        }
        return false
    }
}
