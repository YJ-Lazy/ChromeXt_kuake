package org.matrix.chromext

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import org.matrix.chromext.utils.LogRecorder

class DiagnosticsActivity : Activity() {
  private lateinit var status: TextView
  private lateinit var logText: TextView
  private lateinit var recording: Switch

  private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    title = "ChromeXt 诊断日志"

    val root = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(dp(16), dp(12), dp(16), dp(12))
    }
    root.addView(TextView(this).apply {
      text = "开启记录后，重启夸克并复现问题，再回到这里导出日志。日志保存在本机应用私有目录，不会自动上传。"
      textSize = 14f
    })
    recording = Switch(this).apply {
      text = "记录夸克运行日志"
      isChecked = LogRecorder.isEnabled(this@DiagnosticsActivity)
      setOnCheckedChangeListener { _, checked ->
        LogRecorder.setEnabled(this@DiagnosticsActivity, checked)
        refreshLogs()
      }
    }
    root.addView(recording)
    status = TextView(this).apply { textSize = 13f }
    root.addView(status)

    val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    actions.addView(actionButton("刷新") { refreshLogs() })
    actions.addView(actionButton("分享日志") { shareLogs() })
    actions.addView(actionButton("清空") {
      LogRecorder.clear(this@DiagnosticsActivity)
      refreshLogs()
    })
    root.addView(actions)

    val scroll = ScrollView(this)
    logText = TextView(this).apply {
      textSize = 11f
      typeface = Typeface.MONOSPACE
      setTextIsSelectable(true)
      setPadding(dp(8), dp(8), dp(8), dp(8))
    }
    scroll.addView(logText)
    root.addView(scroll, LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    setContentView(root)
    refreshLogs()
  }

  private fun actionButton(label: String, action: () -> Unit): Button =
      Button(this).apply {
        text = label
        setOnClickListener { action() }
      }

  private fun refreshLogs() {
    logText.text = LogRecorder.read(this)
    status.text = if (recording.isChecked) "日志记录已开启" else "日志记录已关闭"
  }

  private fun shareLogs() {
    startActivity(Intent.createChooser(
        Intent(Intent.ACTION_SEND).apply {
          type = "text/plain"
          putExtra(Intent.EXTRA_SUBJECT, "ChromeXt 夸克诊断日志")
          putExtra(Intent.EXTRA_TEXT, LogRecorder.read(this@DiagnosticsActivity))
        },
        "分享诊断日志",
    ))
  }
}
