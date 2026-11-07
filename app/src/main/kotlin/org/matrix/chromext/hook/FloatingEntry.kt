package org.matrix.chromext.hook

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.PopupMenu
import android.widget.PopupWindow
import android.widget.TextView
import java.util.WeakHashMap
import org.matrix.chromext.Chrome
import org.matrix.chromext.script.Local
import org.matrix.chromext.utils.findMethod
import org.matrix.chromext.utils.hookAfter
import org.matrix.chromext.utils.invokeMethod
import org.matrix.chromext.utils.isUserScript
import org.matrix.chromext.utils.shouldBypassSandbox

/**
 * A browser-independent ChromeXt entry point for Quark.
 *
 * The entry is a small native floating button anchored to each UC/U4 WebView. It does not
 * depend on Quark's text-selection toolbar or Chromium's WindowAndroid app-menu classes.
 */
object FloatingEntryHook : BaseHook() {
  private const val FRONT_END_URL = "https://jing.matrix-transformation.dev/ChromeXt/"
  private const val MENU_MANAGE = 1
  private const val MENU_INSTALL = 2
  private const val MENU_CONSOLE = 3

  private val entries = WeakHashMap<Any, PopupWindow>()

  private fun dp(view: View, value: Int): Int =
      (value * view.resources.displayMetrics.density + 0.5f).toInt()

  private fun dismiss(view: Any) {
    entries.remove(view)?.dismiss()
  }

  private fun showEntry(webView: View) {
    if (!webView.isAttachedToWindow || entries.containsKey(webView)) return
    val context = webView.context
    val button = TextView(context).apply {
      text = "CX"
      textSize = 13f
      setTextColor(Color.WHITE)
      gravity = Gravity.CENTER
      contentDescription = "ChromeXt menu"
      elevation = dp(webView, 8).toFloat()
      background = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(Color.rgb(94, 70, 190))
        setStroke(dp(webView, 1), Color.WHITE)
      }
      setOnClickListener { showMenu(this, webView) }
    }
    val size = dp(webView, 46)
    val popup = PopupWindow(button, size, size, false).apply {
      isTouchable = true
      isOutsideTouchable = false
      isFocusable = false
      setBackgroundDrawable(GradientDrawable().apply { setColor(Color.TRANSPARENT) })
      elevation = dp(webView, 8).toFloat()
    }
    try {
      popup.showAtLocation(webView, Gravity.RIGHT or Gravity.CENTER_VERTICAL, dp(webView, 10), 0)
      entries[webView] = popup
    } catch (error: Throwable) {
      popup.dismiss()
      org.matrix.chromext.utils.Log.ex(error, "Unable to show Quark ChromeXt floating entry")
    }
  }

  private fun showMenu(anchor: View, webView: View) {
    val url = runCatching { Chrome.getUrl(webView) }.getOrNull()
    val menu = PopupMenu(anchor.context, anchor)
    menu.menu.add(0, MENU_MANAGE, 0, "脚本管理")
    if (isUserScript(url)) {
      menu.menu.add(0, MENU_INSTALL, 1, "安装当前脚本")
    }
    menu.menu.add(0, MENU_CONSOLE, 2, "开发者控制台")
    menu.setOnMenuItemClickListener { item ->
      when (item.itemId) {
        MENU_MANAGE -> {
          runCatching {
            webView.invokeMethod(FRONT_END_URL) {
              name == "loadUrl" && parameterTypes.size == 1 &&
                  parameterTypes[0] == String::class.java
            }
          }.onFailure {
            org.matrix.chromext.utils.Log.ex(it, "Unable to open ChromeXt script manager")
          }
          true
        }
        MENU_INSTALL -> {
          if (url != null) {
            Chrome.evaluateJavascript(
                listOf("Symbol.installScript(true);"),
                webView,
                null,
                shouldBypassSandbox(url),
            )
          }
          true
        }
        MENU_CONSOLE -> {
          Chrome.evaluateJavascript(listOf(Local.openEruda), webView)
          true
        }
        else -> false
      }
    }
    menu.show()
  }

  override fun init() {
    findMethod(WebViewHook.WebView!!) { name == "onAttachedToWindow" }
        .hookAfter {
          val webView = it.thisObject as View
          Chrome.updateTab(webView)
          showEntry(webView)
        }

    findMethod(WebViewHook.WebView!!) { name == "onDetachedFromWindow" }
        .hookAfter { dismiss(it.thisObject) }

    isInit = true
  }
}
