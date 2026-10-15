package ai.openclaw.app

import ai.openclaw.app.gateway.GatewayMethod
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.json.JSONObject

/**
 * 生成（絵・動画・声・モーションコミックなど）の進み具合を、通知バーに出す。
 * 生成はPCで続くので、ページを離れてもアプリを閉じても止まらない。ここは、その様子を見せるだけ。
 * 作業中は「いまの作業・経過・残り時間」を、終わったら「完成／失敗」を通知する。
 */
internal class GenerationNotifier(private val context: Context) {
  private val manager = NotificationManagerCompat.from(context)
  /** 作業中だったジョブ（id → 題名）。いなくなったら、完成か失敗を通知する。 */
  private var lastRunning = mapOf<String, String>()
  private var started = false

  private fun ensureChannels() {
    val mgr = context.getSystemService(NotificationManager::class.java)
    mgr.createNotificationChannel(
      NotificationChannel(CHANNEL_PROGRESS, "生成の進み具合", NotificationManager.IMPORTANCE_LOW).apply {
        description = "絵・動画・声などを作っている間の状態と残り時間"
        setShowBadge(false)
      },
    )
    mgr.createNotificationChannel(
      NotificationChannel(CHANNEL_DONE, "生成の完成", NotificationManager.IMPORTANCE_DEFAULT).apply {
        description = "生成が終わったとき"
      },
    )
  }

  private fun kindLabel(kind: String) =
    when (kind) {
      "image" -> "🎨 画像"
      "video" -> "🎬 動画"
      "music" -> "🎵 音楽"
      "manga" -> "📖 漫画"
      "comic" -> "📚 コミック"
      "voice" -> "🎤 声"
      "motion" -> "🎞 モーションコミック"
      "faceswap" -> "🎭 顔入れ替え"
      else -> kind
    }

  private fun minutes(sec: Int): String = if (sec < 60) "1分以内" else "約${(sec + 59) / 60}分"

  private data class Row(
    val id: String,
    val kind: String,
    val status: String,
    val title: String,
    val stage: String,
    val elapsed: Int,
    val estimate: Int,
    val remaining: Int,
    val duration: Int,
    val error: String,
  )

  private fun parse(json: String): List<Row> {
    val arr = JSONObject(json).optJSONArray("jobs") ?: return emptyList()
    return (0 until arr.length()).map { i ->
      val o = arr.getJSONObject(i)
      val owner = o.optString("owner")
      val request = o.optString("request").ifBlank { "（指示なし）" }
      Row(
        id = o.optString("jobId"),
        kind = o.optString("kind"),
        status = o.optString("status"),
        title = (if (owner.isNotBlank()) "【$owner】" else "") + request.take(30),
        stage = o.optString("stage"),
        elapsed = o.optInt("elapsedSec", 0),
        estimate = o.optInt("estimateSec", 0),
        remaining = o.optInt("remainingSec", 0),
        duration = o.optInt("durationSec", 0),
        error = o.optString("error"),
      )
    }
  }

  private fun canNotify() = manager.areNotificationsEnabled()

  private fun showProgress(running: List<Row>) {
    val first = running.first()
    val doing = running.filter { it.status == "running" }.ifEmpty { running }
    val head = doing.first()
    val left = if (head.estimate > 0 && head.status == "running") "　残り${minutes(head.remaining)}" else ""
    val title = "${kindLabel(head.kind)}を作っています" + if (running.size > 1) "（ほか${running.size - 1}件待ち）" else ""
    val text = "${head.stage.ifBlank { "準備中" }}（経過 ${head.elapsed / 60}分）$left"
    val style =
      NotificationCompat.InboxStyle().also { box ->
        running.take(4).forEach { row ->
          val rowLeft = if (row.estimate > 0) (if (row.status == "queued") "・順番待ち 目安${minutes(row.estimate)}" else "・残り${minutes(row.remaining)}") else ""
          box.addLine("${kindLabel(row.kind)} ${row.title} ${row.stage.ifBlank { "" }}$rowLeft".take(90))
        }
      }
    val builder =
      NotificationCompat.Builder(context, CHANNEL_PROGRESS)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(title)
        .setContentText(text)
        .setStyle(style)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(mainActivityPendingIntent(context, requestCode = 3))
    if (head.estimate > 0 && head.status == "running") {
      builder.setProgress(head.estimate, head.elapsed.coerceIn(0, head.estimate), false)
    } else {
      builder.setProgress(0, 0, true)
    }
    if (first.id.isNotBlank()) manager.notify(NOTIFICATION_PROGRESS, builder.build())
  }

  private fun showFinished(row: Row) {
    val ok = row.status == "done"
    val text =
      if (ok) "作成時間 ${row.duration / 60}分${row.duration % 60}秒" else "作れませんでした：${row.error.ifBlank { "原因不明" }}".take(120)
    val notification =
      NotificationCompat.Builder(context, CHANNEL_DONE)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle((if (ok) "✅ " else "⚠️ ") + "${kindLabel(row.kind)}が" + (if (ok) "完成しました" else "失敗しました"))
        .setContentText("${row.title}　$text")
        .setStyle(NotificationCompat.BigTextStyle().bigText("${row.title}\n$text"))
        .setAutoCancel(true)
        .setContentIntent(mainActivityPendingIntent(context, requestCode = 4))
        .build()
    manager.notify(NOTIFICATION_DONE_BASE + (row.id.hashCode() and 0xFFFF), notification)
  }

  /** ゲートウェイにつながっている間、見張り続ける（取り消されるまで）。 */
  suspend fun watch(runtime: NodeRuntime) {
    ensureChannels()
    while (true) {
      var wait = 20_000L
      try {
        val rows = parse(runtime.runGatewayRpc(GatewayMethod.MediaJobList, """{"limit":24}""", 15_000))
        val running = rows.filter { it.status == "running" || it.status == "queued" }
        if (started) {
          // いなくなったジョブは、終わった（完成か失敗）。
          for ((id, _) in lastRunning) {
            if (running.none { it.id == id }) rows.firstOrNull { it.id == id && (it.status == "done" || it.status == "error") }?.let { if (canNotify()) showFinished(it) }
          }
        }
        started = true
        lastRunning = running.associate { it.id to it.title }
        if (running.isEmpty()) {
          manager.cancel(NOTIFICATION_PROGRESS)
        } else {
          if (canNotify()) showProgress(running)
          wait = 10_000L
        }
      } catch (err: CancellationException) {
        manager.cancel(NOTIFICATION_PROGRESS)
        throw err
      } catch (_: Throwable) {
        // つながっていない間は、通知を残して次を待つ。
      }
      delay(wait)
    }
  }

  companion object {
    private const val CHANNEL_PROGRESS = "generation-progress"
    private const val CHANNEL_DONE = "generation-done"
    private const val NOTIFICATION_PROGRESS = 20
    private const val NOTIFICATION_DONE_BASE = 100
  }
}
