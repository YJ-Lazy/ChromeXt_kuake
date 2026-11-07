package org.matrix.chromext.hook

import android.app.Activity
import android.os.Build
import android.os.Handler
import java.lang.ref.WeakReference
import org.matrix.chromext.Chrome
import org.matrix.chromext.Listener
import org.matrix.chromext.script.Local
import org.matrix.chromext.script.ScriptDbManager
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.findField
import org.matrix.chromext.utils.findMethod
import org.matrix.chromext.utils.hookAfter
import org.matrix.chromext.utils.hookBefore
import org.matrix.chromext.utils.invokeMethod

object WebViewHook : BaseHook() {

  var ViewClient: Class<*>? = null
  var ChromeClient: Class<*>? = null
  var WebView: Class<*>? = null
  val records = mutableListOf<WeakReference<Any>>()

  fun evaluateJavascript(code: String?, view: Any?) {
    val webView = (view ?: Chrome.getTab())
    if (code != null && code.length > 0 && webView != null) {
      val webSettings = webView.invokeMethod { name == "getSettings" }
      if (webSettings?.invokeMethod { name == "getJavaScriptEnabled" } == true)
          Handler(Chrome.getContext().mainLooper).post {
            if (Chrome.packageName == "com.quark.browser") {
              Log.d("UC/U4 evaluateJavascript requested (${code.length} chars)")
            }
            webView.invokeMethod(code, null) { name == "evaluateJavascript" }
          }
    }
  }

  private fun onUpdateUrl(url: String, view: Any?) {
    if (url.startsWith("javascript") || view == null) return
    Chrome.updateTab(view)
    ScriptDbManager.invokeScript(url, view)
  }

  private val ucViewClients = mutableSetOf<Class<*>>()
  private val ucChromeClients = mutableSetOf<Class<*>>()

  private fun handleConsoleMessage(chromeClient: Any, consoleMessage: Any) {
    val messageLevel = consoleMessage.invokeMethod { name == "messageLevel" }
    val sourceId = consoleMessage.invokeMethod { name == "sourceId" } as String
    val lineNumber = consoleMessage.invokeMethod { name == "lineNumber" }
    val message = consoleMessage.invokeMethod { name == "message" } as String
    if (messageLevel.toString() == "TIP" &&
        sourceId.startsWith("local://ChromeXt/init") &&
        lineNumber == Local.anchorInChromeXt) {
      val webView =
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            records
                .find {
                  if (Chrome.isQihoo) {
                    val mProvider = findField(WebView!!) { name == "mProvider" }
                    mProvider.get(it.get())
                  } else {
                    it.get()
                  }
                      ?.invokeMethod { name == "getWebChromeClient" } == chromeClient
                }
                ?.get()
          } else Chrome.getTab()
      Listener.startAction(message, webView, chromeClient, sourceId)
    } else {
      Log.d(messageLevel.toString() + ": [${sourceId}@${lineNumber}] ${message}")
    }
  }

  private fun hookUcWebViewClient(clientClass: Class<*>) {
    if (!ucViewClients.add(clientClass)) return
    findMethod(clientClass, true) {
          name == "onPageStarted" && parameterCount == 3
        }
        .hookAfter {
          if (it.args.size >= 2 && it.args[1] is String) {
            Log.d("UC/U4 onPageStarted callback received")
            onUpdateUrl(it.args[1] as String, it.args[0])
          }
        }
    runCatching {
      findMethod(clientClass, true) { name == "onPageFinished" && parameterCount == 2 }
          .hookAfter { Log.d("UC/U4 onPageFinished callback received") }
    }.onFailure { Log.ex(it, "Unable to hook UC/U4 onPageFinished") }
  }

  private fun hookUcWebChromeClient(clientClass: Class<*>) {
    if (!ucChromeClients.add(clientClass)) return
    runCatching {
      findMethod(clientClass, true) { name == "onConsoleMessage" && parameterCount == 1 }
          .hookAfter { handleConsoleMessage(it.thisObject, it.args[0]) }
    }.onFailure { Log.ex(it, "Unable to hook UC/U4 onConsoleMessage") }
  }

  override fun init() {

    // Quark replaces WebChromeClient with app-specific subclasses. Hook the concrete
    // client instance when it is attached instead of only hooking the UC base class.
    if (Chrome.packageName != "com.quark.browser") {
      findMethod(ChromeClient!!, true) { name == "onConsoleMessage" && parameterCount == 1 }
          .hookAfter { handleConsoleMessage(it.thisObject, it.args[0]) }
    }

    findMethod(WebView!!) { name == "setWebChromeClient" }
        .hookAfter {
          val webView = it.thisObject
          records.removeAll(records.filter { it.get() == null || it.get() == webView })
          val client = it.args[0]
          if (client != null) {
            records.add(WeakReference(webView))
            if (Chrome.packageName == "com.quark.browser") {
              hookUcWebChromeClient(client.javaClass)
            }
          }
        }

    if (Chrome.packageName == "com.quark.browser") {
      findMethod(WebView!!) { name == "setWebViewClient" }
          .hookAfter {
            val client = it.args.firstOrNull()
            if (client != null) hookUcWebViewClient(client.javaClass)
          }
    }

    findMethod(WebView!!) { name == "onAttachedToWindow" }
        .hookAfter { Chrome.updateTab(it.thisObject) }

    if (Chrome.packageName != "com.quark.browser") {
      findMethod(ViewClient!!, true) { name == "onPageStarted" }
          // public void onPageStarted (WebView view, String url, Bitmap favicon)
          .hookAfter {
            if (Chrome.isQihoo && it.thisObject::class.java.declaredMethods.size > 1)
                return@hookAfter
            onUpdateUrl(it.args[1] as String, it.args[0])
          }
    }

    findMethod(Activity::class.java) { name == "onStop" }
        .hookBefore { ScriptDbManager.updateScriptStorage() }
    isInit = true
  }
}
