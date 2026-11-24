package ro.priceradar

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Uses the same public Altex page that successfully seeds a product when it is added. */
internal object AltexWebFetcher {
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun fetch(context: Context, url: String): Listing = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { continuation ->
            val page = WebView(context.applicationContext)
            var completed = false
            val activity = context as? Activity
            if (activity != null) {
                page.alpha = .01f
                activity.addContentView(page, FrameLayout.LayoutParams(1, 1))
            }

            fun close() {
                page.stopLoading()
                (page.parent as? ViewGroup)?.removeView(page)
                page.destroy()
            }
            fun fail(message: String) {
                if (completed || !continuation.isActive) return
                completed = true
                close()
                continuation.resumeWithException(IllegalStateException(message))
            }

            page.settings.javaScriptEnabled = true
            page.settings.domStorageEnabled = true
            page.settings.allowFileAccess = false
            page.settings.allowContentAccess = false
            page.settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(page, true)
            page.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, finishedUrl: String) {
                    if (completed) return
                    lateinit var extract: (Int) -> Unit
                    extract = { attempt -> view.postDelayed({
                        if (completed || !continuation.isActive) return@postDelayed
                        view.evaluateJavascript(ALTEX_LISTING_SCRIPT) { encoded ->
                            val listing = RetailerClient().parseAltexWebResult(encoded)
                            if (listing == null && attempt < 3) extract(attempt + 1)
                            else if (listing == null) fail("Altex page did not expose a current price")
                            else if (!completed && continuation.isActive) {
                                completed = true
                                close()
                                continuation.resume(listing)
                            }
                        }
                    }, if (attempt == 0) 700L else 1_200L) }
                    extract(0)
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) fail("Altex page could not load")
                }

                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                    if (request.isForMainFrame) fail("Altex returned HTTP ${response.statusCode}")
                }
            }
            continuation.invokeOnCancellation { page.post { close() } }
            page.loadUrl(url)
        }
    }
}
