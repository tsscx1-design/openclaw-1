package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.gateway.GatewayMethod
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import org.json.JSONObject

/**
 * サンプル画像製造の結果の置き場（アプリの中で1つ）。どの画面でも、項目を選ぶと、その項目のサンプル絵と
 * 既定の指示が出せるように、PC から読んで覚えておく。キーは「opt:<id>」「motion:<id>」「pose:<id>」「custom:<id>」。
 */
internal object SampleStore {
  /** サンプル絵ができている項目。 */
  val available = mutableStateMapOf<String, Long>()

  /** 項目ごとの既定の指示。 */
  val defaults = mutableStateMapOf<String, String>()

  /** 項目ごとの説明（「どう見えるか」。PC のAIが書いたもの）。 */
  val descriptions = mutableStateMapOf<String, String>()

  /** 自分で追加した項目（id・名前・指示）。キャラ作成の「自分で追加した項目」に出す。 */
  val customItems = mutableStateListOf<Triple<String, String, String>>()
  private val bitmaps = mutableMapOf<String, Bitmap>()
  // 一覧に小さく出す用の縮小画像（たくさん並べても重くならないように、小さく読む）
  private val thumbs = object : android.util.LruCache<String, Bitmap>(500) {}
  private val loaded = mutableStateOf(false)

  /** 一覧を PC から読み直す（絵そのものは、見せるときに読む）。 */
  suspend fun refresh(viewModel: MainViewModel): JSONObject? =
    runCatching {
      val res = JSONObject(viewModel.runGatewayRpc(GatewayMethod.SampleList, "{}", timeoutMs = 60_000))
      val samples = res.optJSONObject("samples")
      val defs = res.optJSONObject("defaults")
      val newAvailable = mutableMapOf<String, Long>()
      samples?.keys()?.forEach { key -> newAvailable[key] = samples.getJSONObject(key).optLong("at") }
      // 作り直された絵は、読み直す。
      for ((key, at) in newAvailable) if (available[key] != at) { bitmaps.remove(key); thumbs.remove(key) }
      available.clear()
      available.putAll(newAvailable)
      defaults.clear()
      defs?.keys()?.forEach { key -> defaults[key] = defs.optString(key) }
      val descs = res.optJSONObject("descriptions")
      descriptions.clear()
      descs?.keys()?.forEach { key -> descriptions[key] = descs.optString(key) }
      val custom = res.optJSONObject("subjects")?.optJSONArray("custom")
      customItems.clear()
      if (custom != null) for (i in 0 until custom.length()) custom.getJSONObject(i).let { customItems.add(Triple(it.optString("id"), it.optString("label"), it.optString("instruction"))) }
      loaded.value = true
      res
    }.getOrNull()

  fun hasSample(key: String): Boolean = available.containsKey(key)

  /** サンプル絵を取る（なければ null）。一度読んだものは覚えておく。 */
  suspend fun bitmap(viewModel: MainViewModel, key: String): Bitmap? {
    bitmaps[key]?.let { return it }
    if (!available.containsKey(key)) return null
    return runCatching {
      val res = JSONObject(viewModel.runGatewayRpc(GatewayMethod.SampleImage, JSONObject().put("key", key).toString(), timeoutMs = 60_000))
      val bytes = Base64.decode(res.optString("imageBase64"), Base64.DEFAULT)
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.also { bitmaps[key] = it }
    }.getOrNull()
  }

  /** 一覧用の小さな絵（長辺 200px 前後）。なければ null。 */
  suspend fun thumb(viewModel: MainViewModel, key: String): Bitmap? {
    thumbs.get(key)?.let { return it }
    if (!available.containsKey(key)) return null
    return runCatching {
      val res = JSONObject(viewModel.runGatewayRpc(GatewayMethod.SampleImage, JSONObject().put("key", key).toString(), timeoutMs = 60_000))
      val bytes = Base64.decode(res.optString("imageBase64"), Base64.DEFAULT)
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
      val sample = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / 200)
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })?.also { thumbs.put(key, it) }
    }.getOrNull()
  }

  fun forget(key: String) {
    thumbs.remove(key)
    bitmaps.remove(key)
    available.remove(key)
  }
}
