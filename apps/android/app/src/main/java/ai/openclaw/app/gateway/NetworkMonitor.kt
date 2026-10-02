package ai.openclaw.app.gateway

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Reports newly available app-visible networks, including private LANs without internet validation.
 * Sessions own desired-connection, auth-pause, and readiness decisions. This process-lifetime
 * callback uses the application context, like its owning NodeRuntime.
 *
 * O-1 修正: isOffline StateFlowを追加。UIがオフライン状態を検出して事前通知できる。
 */
internal class NetworkMonitor(
  context: Context,
  private val onNetworkAvailable: () -> Unit,
) {
  private val logTag = "OpenClaw/NetworkMonitor"

  // Coalesce one network's availability episode, not unrelated routes. Wi-Fi returning must
  // wake retries even when cellular never went away; capability churn is not another attachment.
  private val availableNetworks = ConcurrentHashMap.newKeySet<Network>()

  // O-1: オフライン検出用StateFlow
  private val _isOffline = MutableStateFlow(false)
  val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

  private val callback =
    object : ConnectivityManager.NetworkCallback() {
      override fun onAvailable(network: Network) {
        if (!availableNetworks.add(network)) return
        _isOffline.value = false
        try {
          onNetworkAvailable()
        } catch (err: Throwable) {
          Log.w(logTag, "network restore callback threw: ${err.message ?: err::class.java.simpleName}")
        }
      }

      override fun onLost(network: Network) {
        availableNetworks.remove(network)
        _isOffline.value = availableNetworks.isEmpty()
      }
    }

  init {
    try {
      // Registration delivers existing networks too; session readiness guards make that harmless.
      context.getSystemService(ConnectivityManager::class.java)?.registerNetworkCallback(gatewayNetworkRequest(), callback)
    } catch (err: Throwable) {
      Log.w(logTag, "registerNetworkCallback failed: ${err.message ?: err::class.java.simpleName}")
    }
  }
}

/** Include VPNs without dropping the request's normal trusted, unrestricted, app-visible defaults. */
internal fun gatewayNetworkRequest(): NetworkRequest =
  NetworkRequest
    .Builder()
    .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
    .build()
