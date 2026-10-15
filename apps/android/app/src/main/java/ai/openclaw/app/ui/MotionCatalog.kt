package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.gateway.GatewayMethod
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

/** モーションコミックの体位とオプション。PC（ゲートウェイ）の一覧をそのまま使うので、増えたらアプリを入れ直さなくても出る。 */
internal object MotionCatalog {
  /** (id, 名前, グループ) */
  var poses by mutableStateOf<List<Triple<String, String, String>>>(emptyList())

  /** グループ → (id, 名前) */
  var options by mutableStateOf<List<Pair<String, List<Pair<String, String>>>>>(emptyList())

  suspend fun load(viewModel: MainViewModel) {
    runCatching {
      val res = JSONObject(viewModel.runGatewayRpc(GatewayMethod.MotionCatalog, "{}", timeoutMs = 30_000))
      val p = res.optJSONArray("poses")
      poses = (0 until (p?.length() ?: 0)).map { i -> p!!.getJSONObject(i).let { Triple(it.optString("id"), it.optString("label"), it.optString("group")) } }
      val o = res.optJSONArray("options")
      options = (0 until (o?.length() ?: 0)).map { i ->
        val g = o!!.getJSONObject(i)
        val items = g.optJSONArray("items")
        g.optString("group") to (0 until (items?.length() ?: 0)).map { j -> items!!.getJSONObject(j).let { it.optString("id") to it.optString("label") } }
      }
    }
  }
}
