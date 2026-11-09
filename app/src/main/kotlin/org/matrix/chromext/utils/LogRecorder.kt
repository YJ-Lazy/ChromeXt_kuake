package org.matrix.chromext.utils

import android.content.Context
import android.net.Uri
import android.os.Bundle
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Stores diagnostics in the module app process; Quark sends entries through a private IPC provider. */
object LogRecorder {
  private const val PREFS = "diagnostics"
  private const val ENABLED = "recording_enabled"
  private const val MAX_BYTES = 2 * 1024 * 1024L
  private const val FILE_NAME = "quark-diagnostics.log"
  private val PROVIDER = Uri.parse("content://org.matrix.chromext.diagnostics")
  @Volatile private var browserContext: Context? = null
  private val lock = Any()

  fun initialize(context: Context) {
    browserContext = context.applicationContext
    write("INFO", "Diagnostic recorder connected; source=${context.packageName}")
  }

  fun isEnabled(context: Context? = null): Boolean =
      runCatching {
        (context ?: browserContext)?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.getBoolean(ENABLED, true) ?: false
      }.getOrDefault(false)

  fun setEnabled(context: Context, enabled: Boolean) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit().putBoolean(ENABLED, enabled).apply()
    if (enabled) writeLocal(context, "INFO", "Diagnostic recording enabled")
  }

  fun read(context: Context? = null): String {
    val file = logFile(context) ?: return "日志文件尚未创建。请先在夸克中触发一次 ChromeXt。"
    return runCatching {
      val backup = File(file.parentFile, "$FILE_NAME.1")
      (if (backup.exists()) backup.readText() + "\n--- rotated log ---\n" else "") +
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
    val target = context ?: browserContext
    if (target == null) return
    if (target.packageName == "org.matrix.chromext") {
      writeLocal(target, level, message)
      return
    }
    runCatching {
      val extras = Bundle().apply {
        putString("level", level)
        putString("message", message)
      }
      target.contentResolver.call(PROVIDER, "append", null, extras)
    }
  }

  internal fun writeLocal(context: Context, level: String, message: String) {
    if (!isEnabled(context)) return
    runCatching {
      val file = logFile(context) ?: return
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
      (context ?: browserContext)?.let {
        if (it.packageName == "org.matrix.chromext") File(it.filesDir, "diagnostics/$FILE_NAME")
        else null
      }
}
