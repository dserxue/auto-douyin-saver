package com.example.autodouyinsaver

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.util.Log

class ClipboardReaderActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No UI, completely transparent
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            readClipboardAndFinish()
        }
    }

    private fun readClipboardAndFinish() {
        var shareText = ""
        try {
            val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            if (clipboardManager.hasPrimaryClip()) {
                shareText = clipboardManager.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
            }
        } catch (e: Exception) {
            Log.e("ClipboardReader", "Failed to read clipboard: ${e.message}")
        }

        // Send back to AccessibilityService
        AutoDownloadAccessibilityService.instance?.processShareText(shareText)

        // Finish cleanly
        finish()
        overridePendingTransition(0, 0)
    }
}
