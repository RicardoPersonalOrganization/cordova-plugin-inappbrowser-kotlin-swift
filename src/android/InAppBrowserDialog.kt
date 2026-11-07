package org.apache.cordova.inappbrowser

import android.app.Dialog
import android.content.Context

class InAppBrowserDialog(context: Context, theme: Int) : Dialog(context, theme) {
    var inAppBrowser: InAppBrowser? = null

    fun setInAppBroswer(browser: InAppBrowser) {
        this.inAppBrowser = browser
    }

    override fun onBackPressed() {
        if (this.inAppBrowser == null) {
            this.dismiss()
        } else {
            if (this.inAppBrowser!!.hardwareBack() && this.inAppBrowser!!.canGoBack()) {
                this.inAppBrowser!!.goBack()
            } else {
                this.inAppBrowser!!.closeDialog()
            }
        }
    }
}
