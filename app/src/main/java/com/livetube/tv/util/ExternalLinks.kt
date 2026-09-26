package com.livetube.tv.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Single place for handing web links to the system browser.
 *
 * Returns false when no activity can handle the link so callers can show a friendly message
 * instead of crashing on devices without a browser.
 */
object ExternalLinks {
    private val WEB_SCHEMES = setOf("http", "https")

    fun open(context: Context, url: String): Boolean {
        val uri = runCatching { Uri.parse(url.trim()) }.getOrNull() ?: return false
        if (uri.scheme?.lowercase() !in WEB_SCHEMES) return false
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}
