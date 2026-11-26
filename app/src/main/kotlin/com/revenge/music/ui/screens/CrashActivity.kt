package com.revenge.music.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.revenge.music.R
import com.revenge.music.ui.theme.revengeTheme
import com.revenge.music.utils.CrashHandler
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Rendered in the dedicated `:crash` process. It deliberately does not use Hilt: the crash process
 * has no application DI graph, and showing a crash report must remain possible even when start-up
 * dependencies are broken.
 */
class CrashActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    val crashLog =
      intent.getStringExtra(CrashHandler.EXTRA_CRASH_LOG) ?: getString(R.string.crash_no_log)

    setContent {
      revengeTheme(darkTheme = isSystemInDarkTheme()) {
        CrashScreen(
          crashLog = crashLog,
          onClose = { finishAffinity() },
          onShare = { shareCrashLog(crashLog) },
          onCopy = { copyCrashLog(crashLog) },
        )
      }
    }
  }

  private fun copyCrashLog(crashLog: String) {
    val clipboard = getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, crashLog))
    Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
  }

  private fun shareCrashLog(crashLog: String) {
    val sharedAsFile = runCatching { shareCrashLogAsFile(crashLog) }.getOrDefault(false)
    if (!sharedAsFile) {
      runCatching { shareCrashLogAsText(crashLog) }
    }
  }

  private fun shareCrashLogAsFile(crashLog: String): Boolean {
    val file = File(cacheDir, "revenge_crash_${crashFileTimestamp()}.txt")
    file.writeText(crashLog)

    val uri = FileProvider.getUriForFile(this, "$packageName.FileProvider", file)
    val send =
      Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, getString(R.string.crash_report_subject))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
    startActivity(Intent.createChooser(send, getString(R.string.crash_share_title)))
    return true
  }

  private fun shareCrashLogAsText(crashLog: String) {
    val send =
      Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, crashLog)
        putExtra(Intent.EXTRA_SUBJECT, getString(R.string.crash_report_subject))
      }
    startActivity(Intent.createChooser(send, getString(R.string.crash_share_title)))
  }

  private fun crashFileTimestamp(): String =
    SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

  private companion object {
    const val CLIP_LABEL = "CrashLog"
  }
}

/**
 * Crash report viewer. The crash log is plain text semantics, never a content description, and both
 * scroll axes are reachable so an arbitrarily long report is never truncated.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrashScreen(
  crashLog: String,
  onClose: () -> Unit,
  onShare: () -> Unit,
  onCopy: () -> Unit,
) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.crash_title)) },
        actions = {
          IconButton(onClick = onCopy) {
            Icon(
              painter = painterResource(R.drawable.content_copy),
              contentDescription = stringResource(R.string.copy_logs),
            )
          }
          IconButton(onClick = onClose) {
            Icon(
              painter = painterResource(R.drawable.close),
              contentDescription = stringResource(R.string.crash_close),
            )
          }
        },
      )
    },
    floatingActionButton = {
      ExtendedFloatingActionButton(
        icon = {
          Icon(
            painter = painterResource(R.drawable.share),
            contentDescription = null,
          )
        },
        text = { Text(stringResource(R.string.crash_share_logs)) },
        onClick = onShare,
      )
    },
  ) { contentPadding ->
    Column(
      modifier =
        Modifier.fillMaxSize()
          .padding(contentPadding)
          .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
      Text(
        text = stringResource(R.string.crash_description),
        style = MaterialTheme.typography.bodyLarge,
      )
      Spacer(Modifier.height(12.dp))
      Surface(
        modifier = Modifier.fillMaxWidth().weight(1f),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.medium,
      ) {
        Box(
          modifier =
            Modifier.fillMaxSize()
              .verticalScroll(rememberScrollState())
              .padding(12.dp),
        ) {
          Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Text(
              text = crashLog,
              fontFamily = FontFamily.Monospace,
              fontSize = 11.sp,
            )
          }
        }
      }
    }
  }
}

private const val PREVIEW_LOG =
  """
  java.lang.IllegalStateException: Sample crash
    at com.revenge.music.Sample.fail(Sample.kt:42)
    at com.revenge.music.Sample.run(Sample.kt:17)
  Caused by: java.lang.RuntimeException: demo
    at com.revenge.music.Sample.<init>(Sample.kt:9)
  """

@Preview(showBackground = true)
@Composable
private fun CrashScreenLightPreview() {
  revengeTheme(darkTheme = false) {
    CrashScreen(crashLog = PREVIEW_LOG, onClose = {}, onShare = {}, onCopy = {})
  }
}

@Preview(showBackground = true)
@Composable
private fun CrashScreenDarkPreview() {
  revengeTheme(darkTheme = true, pureBlack = true) {
    CrashScreen(crashLog = PREVIEW_LOG, onClose = {}, onShare = {}, onCopy = {})
  }
}
