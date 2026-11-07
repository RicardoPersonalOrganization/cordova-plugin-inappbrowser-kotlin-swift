package org.apache.cordova.inappbrowser

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Parcelable
import android.provider.Browser
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.graphics.Color
import android.net.http.SslError
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.HttpAuthHandler
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.DownloadListener
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView

import org.apache.cordova.CallbackContext
import org.apache.cordova.Config
import org.apache.cordova.CordovaArgs
import org.apache.cordova.CordovaPlugin
import org.apache.cordova.CordovaWebView
import org.apache.cordova.LOG
import org.apache.cordova.PluginManager
import org.apache.cordova.PluginResult
import org.json.JSONException
import org.json.JSONObject

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.ArrayList
import java.util.Arrays
import java.util.List
import java.util.HashMap
import java.util.StringTokenizer

@SuppressLint("SetJavaScriptEnabled")
class InAppBrowser : CordovaPlugin() {

    private val NULL = "null"
    protected val LOG_TAG = "InAppBrowser"
    private val SELF = "_self"
    private val SYSTEM = "_system"
    private val EXIT_EVENT = "exit"
    private val LOCATION = "location"
    private val ZOOM = "zoom"
    private val ZOOMCONTROLS = "zoomcontrols"
    private val HIDDEN = "hidden"
    private val LOAD_START_EVENT = "loadstart"
    private val LOAD_STOP_EVENT = "loadstop"
    private val LOAD_ERROR_EVENT = "loaderror"
    private val DOWNLOAD_EVENT = "download"
    private val MESSAGE_EVENT = "message"
    private val CLEAR_ALL_CACHE = "clearcache"
    private val CLEAR_SESSION_CACHE = "clearsessioncache"
    private val HARDWARE_BACK_BUTTON = "hardwareback"
    private val MEDIA_PLAYBACK_REQUIRES_USER_ACTION = "mediaPlaybackRequiresUserAction"
    private val SHOULD_PAUSE = "shouldPauseOnSuspend"
    private val DEFAULT_HARDWARE_BACK = true
    private val USER_WIDE_VIEW_PORT = "useWideViewPort"
    private val TOOLBAR_COLOR = "toolbarcolor"
    private val CLOSE_BUTTON_CAPTION = "closebuttoncaption"
    private val CLOSE_BUTTON_COLOR = "closebuttoncolor"
    private val LEFT_TO_RIGHT = "lefttoright"
    private val HIDE_NAVIGATION = "hidenavigationbuttons"
    private val NAVIGATION_COLOR = "navigationbuttoncolor"
    private val HIDE_URL = "hideurlbar"
    private val FOOTER = "footer"
    private val FOOTER_COLOR = "footercolor"
    private val BEFORELOAD = "beforeload"
    private val FULLSCREEN = "fullscreen"

    private val TOOLBAR_HEIGHT = 48

    private val customizableOptions = listOf(CLOSE_BUTTON_CAPTION, TOOLBAR_COLOR, NAVIGATION_COLOR, CLOSE_BUTTON_COLOR, FOOTER_COLOR)

    private var dialog: InAppBrowserDialog? = null
    private var inAppWebView: WebView? = null
    private var edittext: EditText? = null
    private var callbackContext: CallbackContext? = null
    private var showLocationBar = true
    private var enableZoom = true
    private var showZoomControls = true
    private var openWindowHidden = false
    private var clearAllCache = false
    private var clearSessionCache = false
    private var hadwareBackButton = true
    private var mediaPlaybackRequiresUserGesture = false
    private var shouldPauseInAppBrowser = false
    private var useWideViewPort = true
    private var mUploadCallback: ValueCallback<Array<Uri>>? = null
    private val FILECHOOSER_REQUESTCODE = 1
    private var closeButtonCaption = ""
    private var closeButtonColor = ""
    private var leftToRight = false
    private var toolbarColor = android.graphics.Color.LTGRAY
    private var hideNavigationButtons = false
    private var navigationButtonColor = ""
    private var hideUrlBar = false
    private var showFooter = false
    private var footerColor = ""
    private var beforeload: String? = null
    private var fullscreen = true
    private var allowedSchemes: Array<String>? = null
    private var currentClient: InAppBrowserClient? = null

    @Throws(JSONException::class)
    override fun execute(action: String, args: CordovaArgs, callbackContext: CallbackContext): Boolean {
        if (action == "open") {
            val url = args.getString(0)
            var t = args.optString(1)
            if (t == null || t == "" || t == NULL) {
                t = SELF
            }
            val target = t
            val features = parseFeature(args.optString(2))

            LOG.d(LOG_TAG, "target = $target")

            this.cordova.activity.runOnUiThread {
                var result = ""
                if (SELF == target) {
                    LOG.d(LOG_TAG, "in self")
                    var shouldAllowNavigation: Boolean? = null
                    if (url.startsWith("javascript:")) {
                        shouldAllowNavigation = true
                    }
                    if (shouldAllowNavigation == null) {
                        try {
                            val iuw = Config::class.java.getMethod("isUrlWhiteListed", String::class.java)
                            shouldAllowNavigation = iuw.invoke(null, url) as Boolean
                        } catch (e: Exception) {
                            LOG.d(LOG_TAG, e.localizedMessage)
                        }
                    }
                    if (shouldAllowNavigation == null) {
                        try {
                            val gpm = webView::class.java.getMethod("getPluginManager")
                            val pm = gpm.invoke(webView) as PluginManager
                            val san = pm::class.java.getMethod("shouldAllowNavigation", String::class.java)
                            shouldAllowNavigation = san.invoke(pm, url) as Boolean
                        } catch (e: Exception) {
                            LOG.d(LOG_TAG, e.localizedMessage)
                        }
                    }
                    if (shouldAllowNavigation == true) {
                        LOG.d(LOG_TAG, "loading in webview")
                        webView.loadUrl(url)
                    } else if (url.startsWith(WebView.SCHEME_TEL)) {
                        try {
                            LOG.d(LOG_TAG, "loading in dialer")
                            val intent = Intent(Intent.ACTION_DIAL)
                            intent.data = Uri.parse(url)
                            cordova.activity.startActivity(intent)
                        } catch (e: Exception) {
                            LOG.e(LOG_TAG, "Error dialing $url: ${e}")
                        }
                    } else {
                        LOG.d(LOG_TAG, "loading in InAppBrowser")
                        this@InAppBrowser.callbackContext = callbackContext
                        result = showWebPage(url, features)
                    }
                } else if (SYSTEM == target) {
                    LOG.d(LOG_TAG, "in system")
                    result = openExternal(url)
                } else {
                    LOG.d(LOG_TAG, "in blank")
                    this@InAppBrowser.callbackContext = callbackContext
                    result = showWebPage(url, features)
                }

                val pluginResult = PluginResult(PluginResult.Status.OK, result)
                pluginResult.isKeepCallback = true
                callbackContext.sendPluginResult(pluginResult)
            }
        } else if (action == "close") {
            closeDialog()
        } else if (action == "loadAfterBeforeload") {
            if (beforeload == null) {
                LOG.e(LOG_TAG, "unexpected loadAfterBeforeload called without feature beforeload=yes")
            }
            val url = args.getString(0)
            this.cordova.activity.runOnUiThread {
                // Min SDK is API 24+ (Android 7.0); simplify client handling.
                (inAppWebView?.webViewClient as? InAppBrowserClient)?.waitForBeforeload = false
                inAppWebView?.loadUrl(url)
            }
        } else if (action == "injectScriptCode") {
            var jsWrapper: String? = null
            if (args.getBoolean(1)) {
                jsWrapper = String.format("(function(){prompt(JSON.stringify([eval(%%s)]), 'gap-iab://%s')})()", callbackContext.callbackId)
            }
            injectDeferredObject(args.getString(0), jsWrapper)
        } else if (action == "injectScriptFile") {
            var jsWrapper: String
            if (args.getBoolean(1)) {
                jsWrapper = String.format("(function(d) { var c = d.createElement('script'); c.src = %%s; c.onload = function() { prompt('', 'gap-iab://%s'); }; d.body.appendChild(c); })(document)", callbackContext.callbackId)
            } else {
                jsWrapper = "(function(d) { var c = d.createElement('script'); c.src = %s; d.body.appendChild(c); })(document)"
            }
            injectDeferredObject(args.getString(0), jsWrapper)
        } else if (action == "injectStyleCode") {
            var jsWrapper: String
            if (args.getBoolean(1)) {
                jsWrapper = String.format("(function(d) { var c = d.createElement('style'); c.innerHTML = %%s; d.body.appendChild(c); prompt('', 'gap-iab://%s');})(document)", callbackContext.callbackId)
            } else {
                jsWrapper = "(function(d) { var c = d.createElement('style'); c.innerHTML = %s; d.body.appendChild(c); })(document)"
            }
            injectDeferredObject(args.getString(0), jsWrapper)
        } else if (action == "injectStyleFile") {
            var jsWrapper: String
            if (args.getBoolean(1)) {
                jsWrapper = String.format("(function(d) { var c = d.createElement('link'); c.rel='stylesheet'; c.type='text/css'; c.href = %%s; d.head.appendChild(c); prompt('', 'gap-iab://%s');})(document)", callbackContext.callbackId)
            } else {
                jsWrapper = "(function(d) { var c = d.createElement('link'); c.rel='stylesheet'; c.type='text/css'; c.href = %s; d.head.appendChild(c); })(document)"
            }
            injectDeferredObject(args.getString(0), jsWrapper)
        } else if (action == "show") {
            this.cordova.activity.runOnUiThread {
                if (dialog != null && !cordova.activity.isFinishing) {
                    dialog?.show()
                }
            }
            val pluginResult = PluginResult(PluginResult.Status.OK)
            pluginResult.isKeepCallback = true
            this.callbackContext?.sendPluginResult(pluginResult)
        } else if (action == "hide") {
            this.cordova.activity.runOnUiThread {
                if (dialog != null && !cordova.activity.isFinishing) {
                    dialog?.hide()
                }
            }
            val pluginResult = PluginResult(PluginResult.Status.OK)
            pluginResult.isKeepCallback = true
            this.callbackContext?.sendPluginResult(pluginResult)
        } else {
            return false
        }
        return true
    }

    override fun onReset() {
        closeDialog()
    }

    override fun onPause(multitasking: Boolean) {
        if (shouldPauseInAppBrowser) {
            inAppWebView?.onPause()
        }
    }

    override fun onResume(multitasking: Boolean) {
        if (shouldPauseInAppBrowser) {
            inAppWebView?.onResume()
        }
    }

    override fun onDestroy() {
        closeDialog()
    }

    private fun injectDeferredObject(source: String, jsWrapper: String?) {
        if (inAppWebView != null) {
            val scriptToInject: String
            if (jsWrapper != null) {
                val jsonEsc = org.json.JSONArray()
                jsonEsc.put(source)
                val jsonRepr = jsonEsc.toString()
                val jsonSourceString = jsonRepr.substring(1, jsonRepr.length - 1)
                scriptToInject = String.format(jsWrapper, jsonSourceString)
            } else {
                scriptToInject = source
            }
            val finalScriptToInject = scriptToInject
            this.cordova.activity.runOnUiThread {
                inAppWebView?.evaluateJavascript(finalScriptToInject, null)
            }
        } else {
            LOG.d(LOG_TAG, "Can't inject code into the system browser")
        }
    }

    private fun parseFeature(optString: String): HashMap<String, String>? {
        if (optString == NULL) {
            return null
        } else {
            val map = HashMap<String, String>()
            val features = StringTokenizer(optString, ",")
            while (features.hasMoreElements()) {
                val option = StringTokenizer(features.nextToken(), "=")
                if (option.hasMoreElements()) {
                    val key = option.nextToken()
                    val value = option.nextToken()
                    var valToUse = value
                    if (!customizableOptions.contains(key)) {
                        valToUse = if (value == "yes" || value == "no") value else "yes"
                    }
                    map[key] = valToUse
                }
            }
            return map
        }
    }

    fun openExternal(url: String): String {
        try {
            var intent: Intent? = null
            intent = Intent(Intent.ACTION_VIEW)
            val uri = Uri.parse(url)
            if ("file" == uri.scheme) {
                intent.setDataAndType(uri, webView.resourceApi.getMimeType(uri))
            } else {
                intent.data = uri
            }
            intent.putExtra(Browser.EXTRA_APPLICATION_ID, cordova.activity.packageName)
            openExternalExcludeCurrentApp(intent)
            return ""
        } catch (e: RuntimeException) {
            LOG.d(LOG_TAG, "InAppBrowser: Error loading url $url: ${e}")
            return e.toString()
        }
    }

    private fun openExternalExcludeCurrentApp(intent: Intent) {
        val currentPackage = cordova.activity.packageName
        var hasCurrentPackage = false

        val pm = cordova.activity.packageManager
        val activities = pm.queryIntentActivities(intent, 0)
        val targetIntents = ArrayList<Intent>()

        for (ri in activities) {
            if (currentPackage != ri.activityInfo.packageName) {
                val targetIntent = intent.clone() as Intent
                targetIntent.`package` = ri.activityInfo.packageName
                targetIntents.add(targetIntent)
            } else {
                hasCurrentPackage = true
            }
        }

        if (!hasCurrentPackage || targetIntents.size == 0) {
            this.cordova.activity.startActivity(intent)
        } else if (targetIntents.size == 1) {
            this.cordova.activity.startActivity(targetIntents[0])
        } else if (targetIntents.size > 0) {
            val chooser = Intent.createChooser(targetIntents.removeAt(targetIntents.size - 1), null)
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, targetIntents.toTypedArray())
            this.cordova.activity.startActivity(chooser)
        }
    }

    fun closeDialog() {
        this.cordova.activity.runOnUiThread {
            val childView = inAppWebView
            if (childView == null) {
                return@runOnUiThread
            }

            childView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    if (dialog != null && !cordova.activity.isFinishing) {
                        dialog?.dismiss()
                        dialog = null
                    }
                }
            }
            childView.loadUrl("about:blank")

            try {
                val obj = JSONObject()
                obj.put("type", EXIT_EVENT)
                sendUpdate(obj, false)
            } catch (ex: JSONException) {
                LOG.d(LOG_TAG, "Should never happen")
            }
        }
    }

    fun goBack() {
        if (this.inAppWebView?.canGoBack() == true) {
            this.inAppWebView?.goBack()
        }
    }

    fun canGoBack(): Boolean {
        return this.inAppWebView?.canGoBack() == true
    }

    fun hardwareBack(): Boolean {
        return hadwareBackButton
    }

    private fun goForward() {
        if (this.inAppWebView?.canGoForward() == true) {
            this.inAppWebView?.goForward()
        }
    }

    private fun navigate(url: String) {
        val imm = this.cordova.activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(edittext?.windowToken, 0)

        if (!url.startsWith("http") && !url.startsWith("file:")) {
            this.inAppWebView?.loadUrl("http://$url")
        } else {
            this.inAppWebView?.loadUrl(url)
        }
        this.inAppWebView?.requestFocus()
    }

    private fun getShowLocationBar(): Boolean {
        return this.showLocationBar
    }

    private fun getInAppBrowser(): InAppBrowser {
        return this
    }

    fun showWebPage(url: String, features: HashMap<String, String>?): String {
        showLocationBar = true
        enableZoom = true
        showZoomControls = true
        openWindowHidden = false
        mediaPlaybackRequiresUserGesture = false

        if (features != null) {
            val show = features[LOCATION]
            if (show != null) {
                showLocationBar = show == "yes"
            }
            if (showLocationBar) {
                val hideNavigation = features[HIDE_NAVIGATION]
                val hideUrl = features[HIDE_URL]
                if (hideNavigation != null) hideNavigationButtons = hideNavigation == "yes"
                if (hideUrl != null) hideUrlBar = hideUrl == "yes"
            }
            val zoom = features[ZOOM]
            if (zoom != null) {
                enableZoom = zoom == "yes"
            }
            val zoomcontrols = features[ZOOMCONTROLS]
            if (zoomcontrols != null) {
                showZoomControls = zoomcontrols == "yes"
            }
            val hidden = features[HIDDEN]
            if (hidden != null) {
                openWindowHidden = hidden == "yes"
            }
            val hardwareBack = features[HARDWARE_BACK_BUTTON]
            if (hardwareBack != null) {
                hadwareBackButton = hardwareBack == "yes"
            } else {
                hadwareBackButton = DEFAULT_HARDWARE_BACK
            }
            val mediaPlayback = features[MEDIA_PLAYBACK_REQUIRES_USER_ACTION]
            if (mediaPlayback != null) {
                mediaPlaybackRequiresUserGesture = mediaPlayback == "yes"
            }
            val cache = features[CLEAR_ALL_CACHE]
            if (cache != null) {
                clearAllCache = cache == "yes"
            } else {
                val cache2 = features[CLEAR_SESSION_CACHE]
                if (cache2 != null) {
                    clearSessionCache = cache2 == "yes"
                }
            }
            val shouldPause = features[SHOULD_PAUSE]
            if (shouldPause != null) {
                shouldPauseInAppBrowser = shouldPause == "yes"
            }
            val wideViewPort = features[USER_WIDE_VIEW_PORT]
            if (wideViewPort != null) {
                useWideViewPort = wideViewPort == "yes"
            }
            val closeButtonCaptionSet = features[CLOSE_BUTTON_CAPTION]
            if (closeButtonCaptionSet != null) {
                closeButtonCaption = closeButtonCaptionSet
            }
            val closeButtonColorSet = features[CLOSE_BUTTON_COLOR]
            if (closeButtonColorSet != null) {
                closeButtonColor = closeButtonColorSet
            }
            val leftToRightSet = features[LEFT_TO_RIGHT]
            leftToRight = leftToRightSet != null && leftToRightSet == "yes"

            val toolbarColorSet = features[TOOLBAR_COLOR]
            if (toolbarColorSet != null) {
                toolbarColor = android.graphics.Color.parseColor(toolbarColorSet)
            }
            val navigationButtonColorSet = features[NAVIGATION_COLOR]
            if (navigationButtonColorSet != null) {
                navigationButtonColor = navigationButtonColorSet
            }
            val showFooterSet = features[FOOTER]
            if (showFooterSet != null) {
                showFooter = showFooterSet == "yes"
            }
            val footerColorSet = features[FOOTER_COLOR]
            if (footerColorSet != null) {
                footerColor = footerColorSet
            }
            if (features[BEFORELOAD] != null) {
                beforeload = features[BEFORELOAD]
            }
            val fullscreenSet = features[FULLSCREEN]
            if (fullscreenSet != null) {
                fullscreen = fullscreenSet == "yes"
            }
        }

        val thatWebView = this.webView

        val runnable = Runnable {
            fun dpToPixels(dipValue: Int): Int {
                return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dipValue.toFloat(), cordova.activity.resources.displayMetrics).toInt()
            }

            fun clearCookies(completion: Runnable) {
                val cookieManager = CookieManager.getInstance()

                if (clearAllCache) {
                    cookieManager.removeAllCookies(ValueCallback<Boolean> {
                        cookieManager.flush()
                        completion.run()
                    })
                } else if (clearSessionCache) {
                    cookieManager.removeSessionCookies(ValueCallback<Boolean> {
                        cookieManager.flush()
                        completion.run()
                    })
                } else {
                    completion.run()
                }
            }

            fun createCloseButton(id: Int): View {
                val activityRes = cordova.activity.resources
                val _close: View
                if (closeButtonCaption != "") {
                    val close = TextView(cordova.activity)
                    close.text = closeButtonCaption
                    close.textSize = 20f
                    if (closeButtonColor != "") close.setTextColor(android.graphics.Color.parseColor(closeButtonColor))
                    close.gravity = Gravity.CENTER_VERTICAL
                    close.setPadding(dpToPixels(10), 0, dpToPixels(10), 0)
                    _close = close
                } else {
                    val close = ImageButton(cordova.activity)
                    val closeResId = activityRes.getIdentifier("ic_action_remove", "drawable", cordova.activity.packageName)
                    val closeIcon = activityRes.getDrawable(closeResId)
                    if (closeButtonColor != "") close.setColorFilter(android.graphics.Color.parseColor(closeButtonColor))
                    close.setImageDrawable(closeIcon)
                    close.scaleType = ImageView.ScaleType.FIT_CENTER
                    close.adjustViewBounds = true
                    _close = close
                }

                val closeLayoutParams = RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
                if (leftToRight) closeLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT) else closeLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
                _close.layoutParams = closeLayoutParams
                _close.background = null
                _close.contentDescription = "Close Button"
                _close.id = id
                _close.setOnClickListener { closeDialog() }
                return _close
            }

            // Minimal WebView-based UI
            dialog = InAppBrowserDialog(cordova.activity, android.R.style.Theme_NoTitleBar)
            dialog?.window?.attributes?.windowAnimations = android.R.style.Animation_Dialog
            dialog?.requestWindowFeature(Window.FEATURE_NO_TITLE)
            if (fullscreen) {
                dialog?.window?.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
            }
            dialog?.setCancelable(true)
            dialog?.setInAppBroswer(getInAppBrowser())

            val main = LinearLayout(cordova.activity)
            main.orientation = LinearLayout.VERTICAL

            val webViewLayout = RelativeLayout(cordova.activity)
            webViewLayout.layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1.0f)

            inAppWebView = WebView(cordova.activity)
            inAppWebView?.layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            inAppWebView?.id = 6
            inAppWebView?.webChromeClient = object : InAppChromeClient(thatWebView) {
                override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
                    return super.onCreateWindow(view, isDialog, isUserGesture, resultMsg)
                }

                override fun onShowFileChooser(webView: WebView?, filePathCallback: ValueCallback<Array<Uri>>?, fileChooserParams: WebChromeClient.FileChooserParams?): Boolean {
                    if (mUploadCallback != null) {
                        mUploadCallback?.onReceiveValue(null)
                    }
                    mUploadCallback = filePathCallback
                    val content = Intent(Intent.ACTION_GET_CONTENT)
                    content.addCategory(Intent.CATEGORY_OPENABLE)
                    content.type = "*/*"
                    cordova.startActivityForResult(this@InAppBrowser, Intent.createChooser(content, "Select File"), FILECHOOSER_REQUESTCODE)
                    return true
                }
            }
            currentClient = InAppBrowserClient(thatWebView, edittext, beforeload)
            inAppWebView?.webViewClient = currentClient
            val settings = inAppWebView?.settings
            settings?.javaScriptEnabled = true
            settings?.javaScriptCanOpenWindowsAutomatically = true
            settings?.setBuiltInZoomControls(enableZoom)
            settings?.displayZoomControls = showZoomControls
            settings?.pluginState = WebSettings.PluginState.ON

            inAppWebView?.setDownloadListener(DownloadListener { url1, userAgent, contentDisposition, mimetype, contentLength ->
                try {
                    val succObj = JSONObject()
                    succObj.put("type", DOWNLOAD_EVENT)
                    succObj.put("url", url1)
                    succObj.put("userAgent", userAgent)
                    succObj.put("contentDisposition", contentDisposition)
                    succObj.put("mimetype", mimetype)
                    succObj.put("contentLength", contentLength)
                    sendUpdate(succObj, true)
                } catch (e: Exception) {
                    LOG.e(LOG_TAG, e.message)
                }
            })

            class JsObject {
                @JavascriptInterface
                fun postMessage(data: String) {
                    try {
                        val obj = JSONObject()
                        obj.put("type", MESSAGE_EVENT)
                        obj.put("data", JSONObject(data))
                        sendUpdate(obj, true)
                    } catch (ex: JSONException) {
                        LOG.e(LOG_TAG, "data object passed to postMessage has caused a JSON error.")
                    }
                }
            }

            settings?.mediaPlaybackRequiresUserGesture = mediaPlaybackRequiresUserGesture
            inAppWebView?.addJavascriptInterface(JsObject(), "cordova_iab")

            val overrideUserAgent = preferences.getString("OverrideUserAgent", null)
            val appendUserAgent = preferences.getString("AppendUserAgent", null)

            if (overrideUserAgent != null) {
                settings?.userAgentString = overrideUserAgent
            }
            if (appendUserAgent != null) {
                settings?.userAgentString = settings?.userAgentString + " " + appendUserAgent
            }

            val appSettings = cordova.activity.intent.extras
            val enableDatabase = appSettings == null || appSettings.getBoolean("InAppBrowserStorageEnabled", true)
            if (enableDatabase) {
                val databasePath = cordova.activity.applicationContext.getDir("inAppBrowserDB", Context.MODE_PRIVATE).path
                settings?.databasePath = databasePath
                settings?.databaseEnabled = true
            }
            settings?.domStorageEnabled = true
            CookieManager.getInstance().setAcceptThirdPartyCookies(inAppWebView, true)

            clearCookies(Runnable { inAppWebView?.loadUrl(url) })
            inAppWebView?.id = 6
            inAppWebView?.settings?.loadWithOverviewMode = true
            inAppWebView?.settings?.useWideViewPort = useWideViewPort
            inAppWebView?.settings?.supportMultipleWindows = true
            inAppWebView?.requestFocus()
            inAppWebView?.requestFocusFromTouch()

            webViewLayout.addView(inAppWebView)
            main.addView(webViewLayout)

            if (dialog != null) {
                dialog?.setContentView(main)
                dialog?.show()
            }
            if (openWindowHidden && dialog != null) {
                dialog?.hide()
            }
        }
        this.cordova.activity.runOnUiThread(runnable)
        return ""
    }

    private fun sendUpdate(obj: JSONObject, keepCallback: Boolean) {
        sendUpdate(obj, keepCallback, PluginResult.Status.OK)
    }

    private fun sendUpdate(obj: JSONObject, keepCallback: Boolean, status: PluginResult.Status) {
        if (this.callbackContext != null) {
            val result = PluginResult(status, obj)
            result.isKeepCallback = keepCallback
            this.callbackContext?.sendPluginResult(result)
            if (!keepCallback) {
                this.callbackContext = null
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        LOG.d(LOG_TAG, "onActivityResult")
        if (requestCode != FILECHOOSER_REQUESTCODE || mUploadCallback == null) {
            super.onActivityResult(requestCode, resultCode, intent)
            return
        }
        mUploadCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, intent))
        mUploadCallback = null
    }

    inner class InAppBrowserClient(var webView: CordovaWebView?, var edittext: EditText?, var beforeload: String?) : android.webkit.WebViewClient() {
        var waitForBeforeload: Boolean = beforeload != null

        @Suppress("OverridingDeprecatedMember")
        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
            return shouldOverrideUrlLoading(url, null)
        }

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            return shouldOverrideUrlLoading(request?.url.toString(), request?.method)
        }

        fun shouldOverrideUrlLoading(url: String?, method: String?): Boolean {
            var override = false
            var useBeforeload = false
            var errorMessage: String? = null

            if (beforeload == "yes" && method == null) {
                useBeforeload = true
            } else if (beforeload == "yes" && method != "POST") {
                useBeforeload = true
            } else if (beforeload == "get" && (method == null || method == "GET")) {
                useBeforeload = true
            } else if (beforeload == "post" && (method == null || method == "POST")) {
                errorMessage = "beforeload doesn't yet support POST requests"
            }

            if (useBeforeload && this.waitForBeforeload) {
                if (sendBeforeLoad(url, method)) {
                    return true
                }
            }

            if (errorMessage != null) {
                try {
                    LOG.e(LOG_TAG, errorMessage)
                    val obj = JSONObject()
                    obj.put("type", LOAD_ERROR_EVENT)
                    obj.put("url", url)
                    obj.put("code", -1)
                    obj.put("message", errorMessage)
                    sendUpdate(obj, true, PluginResult.Status.ERROR)
                } catch (e: Exception) {
                    LOG.e(LOG_TAG, "Error sending loaderror for $url: ${e}")
                }
            }

            if (url != null && url.startsWith(WebView.SCHEME_TEL)) {
                try {
                    val intent = Intent(Intent.ACTION_DIAL)
                    intent.data = Uri.parse(url)
                    cordova.activity.startActivity(intent)
                    override = true
                } catch (e: Exception) {
                    LOG.e(LOG_TAG, "Error dialing $url: ${e}")
                }
            } else if (url != null && (url.startsWith("geo:") || url.startsWith(WebView.SCHEME_MAILTO) || url.startsWith("market:") || url.startsWith("intent:"))) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.data = Uri.parse(url)
                    cordova.activity.startActivity(intent)
                    override = true
                } catch (e: Exception) {
                    LOG.e(LOG_TAG, "Error with $url: ${e}")
                }
            } else if (url != null && url.startsWith("sms:")) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW)
                    var address: String? = null
                    val parmIndex = url.indexOf('?')
                    if (parmIndex == -1) {
                        address = url.substring(4)
                    } else {
                        address = url.substring(4, parmIndex)
                        val uri = Uri.parse(url)
                        val query = uri.query
                        if (query != null) {
                            if (query.startsWith("body=")) {
                                intent.putExtra("sms_body", query.substring(5))
                            }
                        }
                    }
                    intent.data = Uri.parse("sms:" + address)
                    intent.putExtra("address", address)
                    intent.type = "vnd.android-dir/mms-sms"
                    cordova.activity.startActivity(intent)
                    override = true
                } catch (e: Exception) {
                    LOG.e(LOG_TAG, "Error sending sms $url: ${e}")
                }
            } else if (url != null && !url.startsWith("http:") && !url.startsWith("https:") && url.matches(Regex("^[A-Za-z0-9+.-]*://.*?$"))) {
                if (allowedSchemes == null) {
                    val allowed = preferences.getString("AllowedSchemes", null)
                    if (allowed != null) {
                        allowedSchemes = allowed.split(",").toTypedArray()
                    }
                }
                if (allowedSchemes != null) {
                    for (scheme in allowedSchemes!!) {
                        if (url.startsWith(scheme)) {
                            try {
                                val obj = JSONObject()
                                obj.put("type", "customscheme")
                                obj.put("url", url)
                                sendUpdate(obj, true)
                                override = true
                            } catch (ex: JSONException) {
                                LOG.e(LOG_TAG, "Custom Scheme URI passed in has caused a JSON error.")
                            }
                        }
                    }
                }
            }

            if (useBeforeload) {
                this.waitForBeforeload = true
            }
            return override
        }

        private fun sendBeforeLoad(url: String?, method: String?): Boolean {
            try {
                val obj = JSONObject()
                obj.put("type", BEFORELOAD)
                obj.put("url", url)
                if (method != null) {
                    obj.put("method", method)
                }
                sendUpdate(obj, true)
                return true
            } catch (ex: JSONException) {
                LOG.e(LOG_TAG, "URI passed in has caused a JSON error.")
            }
            return false
        }

        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
            return super.shouldInterceptRequest(view, request)
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            var newloc = ""
            if (url != null && (url.startsWith("http:") || url.startsWith("https:") || url.startsWith("file:"))) {
                newloc = url
            } else {
                LOG.e(LOG_TAG, "Possible Uncaught/Unknown URI")
                newloc = "http://$url"
            }

            if (newloc != edittext?.text.toString()) {
                edittext?.setText(newloc)
            }

            try {
                val obj = JSONObject()
                obj.put("type", LOAD_START_EVENT)
                obj.put("url", newloc)
                sendUpdate(obj, true)
            } catch (ex: JSONException) {
                LOG.e(LOG_TAG, "URI passed in has caused a JSON error.")
            }
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)

            if (beforeload != null && beforeload!!.isNotEmpty()) {
                this.waitForBeforeload = true
            }

            injectDeferredObject("window.webkit={messageHandlers:{cordova_iab:cordova_iab}}", null)

            CookieManager.getInstance().flush()

            view?.clearFocus()
            view?.requestFocus()

            try {
                val obj = JSONObject()
                obj.put("type", LOAD_STOP_EVENT)
                obj.put("url", url)
                sendUpdate(obj, true)
            } catch (ex: JSONException) {
                LOG.d(LOG_TAG, "Should never happen")
            }
        }

        override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
            super.onReceivedError(view, errorCode, description, failingUrl)

            if (beforeload != null && beforeload!!.isNotEmpty()) {
                this.waitForBeforeload = true
            }

            try {
                val obj = JSONObject()
                obj.put("type", LOAD_ERROR_EVENT)
                obj.put("url", failingUrl)
                obj.put("code", errorCode)
                obj.put("message", description)
                sendUpdate(obj, true, PluginResult.Status.ERROR)
            } catch (ex: JSONException) {
                LOG.d(LOG_TAG, "Should never happen")
            }
        }

        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
            super.onReceivedSslError(view, handler, error)
            try {
                val obj = JSONObject()
                obj.put("type", LOAD_ERROR_EVENT)
                obj.put("url", error?.url)
                obj.put("code", 0)
                obj.put("sslerror", error?.primaryError)
                val message: String = when (error?.primaryError) {
                    SslError.SSL_DATE_INVALID -> "The date of the certificate is invalid"
                    SslError.SSL_EXPIRED -> "The certificate has expired"
                    SslError.SSL_IDMISMATCH -> "Hostname mismatch"
                    SslError.SSL_INVALID -> "A generic error occurred"
                    SslError.SSL_NOTYETVALID -> "The certificate is not yet valid"
                    SslError.SSL_UNTRUSTED -> "The certificate authority is not trusted"
                    else -> "A generic error occurred"
                }
                obj.put("message", message)

                sendUpdate(obj, true, PluginResult.Status.ERROR)
            } catch (ex: JSONException) {
                LOG.d(LOG_TAG, "Should never happen")
            }
            handler?.cancel()
        }

        override fun onReceivedHttpAuthRequest(view: WebView?, handler: HttpAuthHandler?, host: String?, realm: String?) {
            var pluginManager: PluginManager? = null
            try {
                val gpm = webView?.javaClass?.getMethod("getPluginManager")
                pluginManager = gpm?.invoke(webView) as PluginManager
            } catch (e: Exception) {
                LOG.d(LOG_TAG, e.localizedMessage)
            }

            if (pluginManager != null && pluginManager.onReceivedHttpAuthRequest(webView, org.apache.cordova.CordovaHttpAuthHandler(handler), host, realm)) {
                return
            }

            super.onReceivedHttpAuthRequest(view, handler, host, realm)
        }
    }
}
