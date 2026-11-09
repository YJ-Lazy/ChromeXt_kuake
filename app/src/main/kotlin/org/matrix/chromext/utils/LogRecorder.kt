package org.matrix.chromext.utils

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Persistent, private diagnostic log shared by the Quark hook process and the module UI. */
object LogRecorder {
  private const val PREFS = "diagnostics"
  private const val ENABLED = "recording_enabled"
  private const val MAX_BYTES = 2 * 1024 * 1024L
  private const val FILE_NAME = "quark-diagnostics.log"

  @Volatile private var moduleContext: Context? = null
  private val lock = Any()

  fun initialize(context: Context) {
    moduleContext = runCatching {
      if (context.packageName == "org.matrix.chromext") context
      else context.createPackageContext("org.matrix.chromext", Context.CONTEXT_IGNORE_SECURITY)
    }.getOrNull()
    write("INFO", "Diagnostic recorder initialized; source=${context.packageName}")
  }

  private fun resolveContext(provided: Context? = null): Context? = provided ?: moduleContext

  fun isEnabled(context: Context? = null): Boolean =
      runCatching {
        resolveContext(context)?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.getBoolean(ENABLED, true) ?: false
      }.getOrDefault(false)

  fun setEnabled(context: Context, enabled: Boolean) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit().putBoolean(ENABLED, enabled).apply()
    if (enabled) write("INFO", "Diagnostic recording enabled", context)
  }

  fun read(context: Context? = null): String {
    val file = logFile(context) ?: return "日志文件尚未创建。请先在夸克中触发一次 ChromeXt。"
    return runCatching {
      val backup = File(file.parentFile, "$FILE_NAME.1")
      (if (backup.exists()) backup.readText() + "\\n--- rotated log ---\\n" else "") +
          if (file.exists()) file.readText() else "暂无日志。"
    }.getOrElse { "读取日志失败：${it}" }
  }

  fun clear(context: Context? = null): Boolean =
      runCatching {
        val file = logFile(context) ?: return false
        file.parentFile?.mkdirs()
        file.writeText("")
        File(file.parentFile, "$FILE_NAME.1").delete()
        true
      }.getOrDefault(false)

  fun write(level: String, message: String, context: Context? = null) {
    if (!isEnabled(context)) return
    val target = resolveContext(context) ?: return
    runCatching {
      val file = File(target.filesDir, "diagnostics/$FILE_NAME")
      synchronized(lock) {
        file.parentFile?.mkdirs()
        if (file.exists() && file.length() >= MAX_BYTES) {
          val backup = File(file.parentFile, "$FILE_NAME.1")
          if (backup.exists()) backup.delete()
          file.renameTo(backup)
        }
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        file.appendText("$stamp [${Thread.currentThread().name}] [$level] $message\n")
      }
    }
  }

  private fun logFile(context: Context? = null): File? =
      resolveContext(context)?.let { File(it.filesDir, "diagnostics/$FILE_NAME") }
}
