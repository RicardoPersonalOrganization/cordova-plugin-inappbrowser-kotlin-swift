import Foundation
import UIKit
import WebKit
import Cordova

@objcMembers
public class InAppBrowserViewController: UIViewController, WKNavigationDelegate, WKScriptMessageHandler, UIAdaptivePresentationControllerDelegate {
    public var webView: WKWebView!
    public var configuration: WKWebViewConfiguration!
    public var closeButton: UIBarButtonItem!
    public var addressBackgroundView: UIView!
    public var addressLabel: UILabel!
    public var backButton: UIBarButtonItem!
    public var forwardButton: UIBarButtonItem!
    public var spinner: UIActivityIndicatorView!
    public var toolbarBackground: UIView!
    public var toolbar: UIToolbar!
    public weak var navigationDelegate: InAppBrowserNavigationDelegate?
    public var currentURL: URL?
    var isExiting: Bool = false

    var browserOptions: InAppBrowserOptions!
    var settings: [String: Any] = [:]

    public init(browserOptions: InAppBrowserOptions, settings: [String: Any]) {
        super.init(nibName: nil, bundle: nil)
        self.browserOptions = browserOptions
        self.settings = settings
        self.createViews()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
    }

    func createViews() {
        let userContentController = WKUserContentController()
        userContentController.add(self, name: IAB_BRIDGE_NAME)
        configuration = WKWebViewConfiguration()
        configuration.userContentController = userContentController
        configuration.allowsInlineMediaPlayback = browserOptions.allowinlinemediaplayback
        webView = WKWebView(frame: .zero, configuration: configuration)
        webView.navigationDelegate = self
        webView.configuration.userContentController = userContentController

        view.addSubview(webView)
        webView.translatesAutoresizingMaskIntoConstraints = false

        toolbarBackground = UIView()
        toolbarBackground.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(toolbarBackground)

        toolbar = UIToolbar(frame: .zero)
        toolbar.translatesAutoresizingMaskIntoConstraints = false
        toolbarBackground.addSubview(toolbar)

        addressBackgroundView = UIView()
        addressBackgroundView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(addressBackgroundView)

        addressLabel = UILabel()
        addressLabel.text = "Loading..."
        addressLabel.translatesAutoresizingMaskIntoConstraints = false
        addressBackgroundView.addSubview(addressLabel)

        spinner = UIActivityIndicatorView(style: .medium)
        spinner.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(spinner)

        closeButton = UIBarButtonItem(barButtonSystemItem: .done, target: self, action: #selector(close))

        forwardButton = UIBarButtonItem(title: "►", style: .plain, target: self, action: #selector(goForward))
        backButton = UIBarButtonItem(title: "◄", style: .plain, target: self, action: #selector(goBack))

        let flexible = UIBarButtonItem(barButtonSystemItem: .flexibleSpace, target: nil, action: nil)

        if browserOptions.hidenavigationbuttons {
            toolbar.items = [flexible, closeButton]
        } else {
            if browserOptions.lefttoright {
                toolbar.items = [backButton, flexible, closeButton]
            } else {
                toolbar.items = [closeButton, flexible, backButton]
            }
        }

        // constraints: webView full, toolbar at bottom or top depending on options omitted for brevity
        NSLayoutConstraint.activate([
            webView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            webView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            webView.topAnchor.constraint(equalTo: view.topAnchor),
            webView.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
    }

    @objc public func close() {
        currentURL = nil
        isExiting = true
        DispatchQueue.main.async {
            self.dismiss(animated: true, completion: nil)
        }
    }

    public override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        if isExiting {
            isExiting = false
            navigationDelegate?.browserExit()
        }
    }

    // MARK: - UIAdaptivePresentationControllerDelegate
    public func presentationControllerWillDismiss(_ presentationController: UIPresentationController) {
        // Called when an interactive dismissal begins (swipe down on sheet)
        isExiting = true
    }

    @objc public func navigateTo(_ url: URL) {
        if url.isFileURL {
            webView.loadFileURL(url, allowingReadAccessTo: url)
        } else {
            let req = URLRequest(url: url)
            webView.load(req)
        }
    }

    @objc public func goBack(_ sender: Any?) {
        webView.goBack()
    }

    @objc public func goForward(_ sender: Any?) {
        webView.goForward()
    }

    // MARK: - Toolbar / Address Helpers
    @objc public func setCloseButtonTitle(_ title: String?, withColor colorString: String?, atIndex buttonIndex: Int) {
        // Create close button either with title or system item
        if let title = title {
            self.closeButton = UIBarButtonItem(title: title, style: .plain, target: self, action: #selector(close))
        } else {
            self.closeButton = UIBarButtonItem(barButtonSystemItem: .done, target: self, action: #selector(close))
        }
        if let colorString = colorString {
            self.closeButton.tintColor = colorFromHexString(colorString)
        } else {
            self.closeButton.tintColor = UIColor(red: 60.0/255.0, green: 136.0/255.0, blue: 230.0/255.0, alpha: 1)
        }

        var items = self.toolbar.items ?? []
        if buttonIndex >= 0 && buttonIndex < items.count {
            items[buttonIndex] = self.closeButton
        } else {
            // Fallback: append or insert at index 0
            if items.count > 0 { items[0] = self.closeButton } else { items.append(self.closeButton) }
        }
        self.toolbar.setItems(items, animated: false)
    }

    @objc public func showLocationBar(_ show: Bool) {
        self.addressBackgroundView.isHidden = !show
        self.view.setNeedsLayout()
        self.view.layoutIfNeeded()
    }

    @objc public func showToolBar(_ show: Bool, atPosition toolbarPosition: String) {
        self.toolbarBackground.isHidden = !show
        // store position if needed
        // simple implementation: no-op for position
        self.view.setNeedsLayout()
        self.view.layoutIfNeeded()
    }

    @objc public func setWebViewFrame(_ frame: CGRect) {
        DispatchQueue.main.async {
            NSLog("Setting the WebView's frame to \(NSStringFromCGRect(frame))")
            self.webView.frame = frame
        }
    }

    func colorFromHexString(_ hexString: String) -> UIColor {
        var cString = hexString.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        if cString.hasPrefix("#") { cString.removeFirst() }
        if cString.count == 3 {
            // e.g. FFF -> FFFFFF
            var full = ""
            for ch in cString { full.append(String(repeating: ch, count: 2)) }
            cString = full
        }
        if cString.count == 6 {
            var rgbValue: UInt64 = 0
            Scanner(string: cString).scanHexInt64(&rgbValue)
            let r = CGFloat((rgbValue & 0xFF0000) >> 16) / 255.0
            let g = CGFloat((rgbValue & 0x00FF00) >> 8) / 255.0
            let b = CGFloat(rgbValue & 0x0000FF) / 255.0
            return UIColor(red: r, green: g, blue: b, alpha: 1.0)
        }
        return UIColor.systemBlue
    }

    // MARK: - WKScriptMessageHandler
    public func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        if let navDelegate = navigationDelegate as? InAppBrowserNavigationDelegate {
            navDelegate.userContentController(userContentController, didReceiveScriptMessage: message)
        }
    }

    // MARK: - WKNavigationDelegate forwarding
    public func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        if let navDelegate = navigationDelegate as? InAppBrowserNavigationDelegate {
            navDelegate.webView(webView, decidePolicyFor: navigationAction, decisionHandler: decisionHandler)
        } else {
            decisionHandler(.allow)
        }
    }

    public func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
        if let navDelegate = navigationDelegate as? InAppBrowserNavigationDelegate {
            navDelegate.didStartProvisionalNavigation(webView)
        }
    }

    public func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        if let navDelegate = navigationDelegate as? InAppBrowserNavigationDelegate {
            navDelegate.didFinishNavigation(webView)
        }
    }

    public func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        if let navDelegate = navigationDelegate as? InAppBrowserNavigationDelegate {
            navDelegate.didFailNavigation(webView, withError: error)
        }
    }
}
