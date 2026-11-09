package org.matrix.chromext

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import org.matrix.chromext.utils.LogRecorder

class DiagnosticsProvider : ContentProvider() {
  override fun onCreate(): Boolean = true

  private fun allowedCaller(): Boolean {
    val ctx = context ?: return false
    val packages = ctx.packageManager.getPackagesForUid(Binder.getCallingUid()).orEmpty()
    return packages.any { it == "com.quark.browser" || it == ctx.packageName }
  }

  override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
    if (!allowedCaller()) throw SecurityException("Caller is not an allowed diagnostics client")
    val ctx = context ?: throw IllegalStateException("Provider has no context")
    return when (method) {
      "append" -> {
        LogRecorder.writeLocal(
            ctx,
            extras?.getString("level") ?: "INFO",
            extras?.getString("message") ?: "",
        )
        Bundle.EMPTY
      }
      "is_enabled" -> Bundle().apply {
        putBoolean("enabled", LogRecorder.isEnabled(ctx))
      }
      else -> throw IllegalArgumentException("Unknown diagnostics operation")
    }
  }

  override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
      selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
  override fun getType(uri: Uri): String? = null
  override fun insert(uri: Uri, values: ContentValues?): Uri? = null
  override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
  override fun update(uri: Uri, values: ContentValues?, selection: String?,
      selectionArgs: Array<out String>?): Int = 0
}
