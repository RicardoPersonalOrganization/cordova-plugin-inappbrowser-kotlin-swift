package org.apache.cordova.inappbrowser

import android.annotation.TargetApi
import android.os.Build
import android.os.Message
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import org.apache.cordova.CordovaWebView

open class InAppChromeClient(private val webView: CordovaWebView) : WebChromeClient() {
    private val LOG_TAG = "InAppChromeClient"
    private val MAX_QUOTA = 100 * 1024 * 1024L

    override fun onExceededDatabaseQuota(url: String?, databaseIdentifier: String?, currentQuota: Long, estimatedSize: Long, totalUsedQuota: Long, quotaUpdater: WebStorage.QuotaUpdater) {
        quotaUpdater.updateQuota(MAX_QUOTA)
    }

    override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
        super.onGeolocationPermissionsShowPrompt(origin, callback)
        callback?.invoke(origin, true, false)
    }

    override fun onPermissionRequest(request: PermissionRequest?) {
        // PermissionRequest is available on modern Android versions targeted by this plugin.
        request?.grant(request?.resources)
    }

    override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
        val inAppWebView = view
        val webViewClient = object : android.webkit.WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                inAppWebView?.loadUrl(request?.url.toString())
                return true
            }

            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                inAppWebView?.loadUrl(url)
                return true
            }
        }

        val newWebView = WebView(view?.context)
        newWebView.webViewClient = webViewClient

        val transport = resultMsg?.obj as WebView.WebViewTransport
        transport.webView = newWebView
        resultMsg.sendToTarget()

        return true
    }
}
