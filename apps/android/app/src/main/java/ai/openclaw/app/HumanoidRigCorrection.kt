package ai.openclaw.app

import org.json.JSONArray
import org.json.JSONObject

/** Model-specific corrections; generated bones and weights are rebuilt from the original GLB. */
internal data class HumanoidRigCorrection(
  val enabled: Boolean = false,
  val shoulderWidth: Double = 0.24,
  val hipHeight: Double = 0.53,
  val kneeHeight: Double = 0.28,
  val armAngle: Double = 0.0,
) {
  init {
    require(shoulderWidth.isFinite() && shoulderWidth in 0.16..0.32)
    require(hipHeight.isFinite() && hipHeight in 0.42..0.62)
    require(kneeHeight.isFinite() && kneeHeight in 0.18..0.34)
    require(armAngle.isFinite() && armAngle in 0.0..60.0)
  }

  fun profile(): JSONObject = JSONObject()
    .put("shoulderWidth", shoulderWidth).put("hipHeight", hipHeight)
    .put("kneeHeight", kneeHeight).put("armAngle", armAngle)

  fun encode(): String = profile().put("version", 1).put("enabled", enabled).toString()

  companion object {
    fun storageKey(gatewayId: String, ownerId: String, modelId: String): String {
      require(listOf(gatewayId, ownerId, modelId).all { it.isNotBlank() && it.length <= 512 })
      // Structured components prevent collisions between different gateway/character/model IDs.
      return "humanoid-rig:" + JSONArray(listOf(gatewayId, ownerId, modelId)).toString()
    }

    fun decode(value: String): HumanoidRigCorrection {
      require(value.length <= 2048) { "Saved rig correction is too large" }
      val json = JSONObject(value)
      require(json.getInt("version") == 1) { "Unsupported rig correction version" }
      return HumanoidRigCorrection(json.getBoolean("enabled"), json.getDouble("shoulderWidth"),
        json.getDouble("hipHeight"), json.getDouble("kneeHeight"), json.getDouble("armAngle"))
    }
  }
}
