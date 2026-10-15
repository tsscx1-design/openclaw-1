package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.gateway.GatewayMethod
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * サンプルの生成の順番待ち。画面とは別に動くので、他のタブへ移っても、生成は最後まで続く。
 * 「作る」を押した分は、すぐPCの順番待ちに入り、PCが1つずつ作る（待たずに、次を頼める）。
 */
internal object SampleJobs {
  data class Entry(val key: String, val label: String, val stage: String = "順番待ち")

  /** 作成中・順番待ちの項目（先頭から順に作られる）。 */
  val active = mutableStateListOf<Entry>()

  /** 最後の出来事（できました／失敗しました）。 */
  val message = mutableStateOf("")

  /** 1つ終わるたびに増える。画面が一覧を読み直す合図。 */
  val finished = mutableIntStateOf(0)

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  /** まとめて作っている区切り：区切りの名前 → その中の項目のキー。作っている間、各項目は「作成中」に見える。 */
  private val batches = androidx.compose.runtime.mutableStateMapOf<String, Set<String>>()

  fun isActive(key: String): Boolean = active.any { it.key == key } || batches.values.any { key in it }

  fun stageOf(key: String): String? = active.firstOrNull { it.key == key }?.stage

  /**
   * 区切りの未作成の項目を、まとめて作る。PC側では1つの仕事になり、モデルの読み込みが減り、2枚のGPUで分けて作る。
   * 作っている間は、少しずつ見本が増える（30秒ごとに一覧を読み直す）。1つずつ作る方法はそのまま残る。
   */
  fun enqueueBatch(viewModel: MainViewModel, batchKey: String, label: String, keys: List<String>, params: JSONObject) {
    if (keys.isEmpty() || active.any { it.key == batchKey }) return
    active.add(Entry(batchKey, label))
    batches[batchKey] = keys.toSet()
    fun setStage(stage: String) {
      val index = active.indexOfFirst { it.key == batchKey }
      if (index >= 0) active[index] = active[index].copy(stage = stage)
    }
    scope.launch {
      var nothingToMake = false
      runCatching {
        val body = JSONObject(params.toString()).put("keys", org.json.JSONArray(keys))
        val start = JSONObject(viewModel.runGatewayRpc(GatewayMethod.SampleGenerateBatch, body.toString(), timeoutMs = 60_000))
        if (!start.optBoolean("started")) {
          nothingToMake = true
          return@runCatching
        }
        val jobId = start.optString("jobId")
        var misses = 0
        var polls = 0
        while (true) {
          delay(3_000)
          val now =
            runCatching { JSONObject(viewModel.runGatewayRpc(GatewayMethod.MediaJobGet, JSONObject().put("jobId", jobId).toString(), timeoutMs = 15_000)) }
              .getOrNull()
          if (now == null) {
            if (++misses > 200) throw IllegalStateException("PCと長く連絡できませんでした")
            continue
          }
          misses = 0
          val stage = now.optString("stage")
          setStage(stage.ifBlank { if (now.optString("status") == "queued") "順番待ち" else "作っています" })
          if (++polls % 10 == 0) {
            SampleStore.refresh(viewModel)
            finished.intValue++
          }
          when (now.optString("status")) {
            "done" -> break
            "error" -> throw IllegalStateException(now.optString("error"))
          }
        }
      }.onSuccess { message.value = if (nothingToMake) "未作成の項目はありませんでした：$label" else "できました：$label" }
        .onFailure { message.value = "失敗しました：$label　${it.message}" }
      active.removeAll { it.key == batchKey }
      batches.remove(batchKey)
      SampleStore.refresh(viewModel)
      finished.intValue++
    }
  }

  /** 順番待ちに入れる。同じ項目がすでに入っていれば、何もしない。 */
  fun enqueue(viewModel: MainViewModel, key: String, label: String, method: GatewayMethod, params: JSONObject) {
    if (isActive(key)) return
    active.add(Entry(key, label))
    fun setStage(stage: String) {
      val index = active.indexOfFirst { it.key == key }
      if (index >= 0) active[index] = active[index].copy(stage = stage)
    }
    scope.launch {
      runCatching {
        val job = JSONObject(viewModel.runGatewayRpc(method, params.toString(), timeoutMs = 30_000))
        val jobId = job.optString("jobId")
        var misses = 0
        while (true) {
          delay(3_000)
          val now =
            runCatching { JSONObject(viewModel.runGatewayRpc(GatewayMethod.MediaJobGet, JSONObject().put("jobId", jobId).toString(), timeoutMs = 15_000)) }
              .getOrNull()
          if (now == null) {
            // 通信が一時的に切れても、PC側の作成は続いている。しばらく待ち直す。
            if (++misses > 100) throw IllegalStateException("PCと長く連絡できませんでした")
            continue
          }
          misses = 0
          val stage = now.optString("stage")
          setStage(stage.ifBlank { if (now.optString("status") == "queued") "順番待ち" else "作っています" })
          when (now.optString("status")) {
            "done" -> break
            "error" -> throw IllegalStateException(now.optString("error"))
          }
        }
      }.onSuccess { message.value = "できました：$label" }
        .onFailure { message.value = "失敗しました：$label　${it.message}" }
      active.removeAll { it.key == key }
      SampleStore.refresh(viewModel)
      finished.intValue++
    }
  }
}
