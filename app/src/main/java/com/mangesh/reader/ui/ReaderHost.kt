package com.mangesh.reader.ui

/**
 * Holds the one WebView for the whole session so switching tabs re-attaches it
 * instead of rebuilding and reloading it. Lives in the activity's composition.
 */
class ReaderHost {
    var web: ReaderWebView? = null
}
