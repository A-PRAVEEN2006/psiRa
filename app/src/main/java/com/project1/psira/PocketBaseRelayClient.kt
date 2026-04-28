package com.project1.psira

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Handles communication with a self-hosted PocketBase local relay server.
 * Operates purely on a LAN (intranet) when internet is not available.
 */
class PocketBaseRelayClient(private val context: Context, private val relayIp: String = "192.168.1.100") {

    private val baseUrl = "http://$relayIp:8090/api/collections/payloads/records"

    /**
     * Sends an encrypted payload to the PocketBase relay.
     */
    suspend fun sendPayload(packetId: String, targetId: String, alias: String, message: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL(baseUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json; utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.doOutput = true

            val jsonParam = JSONObject()
            jsonParam.put("packetId", packetId)
            jsonParam.put("targetId", targetId)
            jsonParam.put("alias", alias)
            jsonParam.put("payload", message) // This is the encrypted payload

            val out = OutputStreamWriter(connection.outputStream)
            out.write(jsonParam.toString())
            out.flush()
            out.close()

            val responseCode = connection.responseCode
            return@withContext responseCode in 200..299
        } catch (e: Exception) {
            Log.e("PocketBaseRelay", "Error sending payload: ${e.message}")
            return@withContext false
        }
    }

    /**
     * Detects if the device is connected to a local Wi-Fi network, regardless of internet access.
     */
    fun isConnectedToLan(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        // Check if it's Wi-Fi (could be a local router with no internet)
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /**
     * Determines if we should route through PocketBase instead of Firebase.
     * Logic: If we are on Wi-Fi but do NOT have the INTERNET capability (or we prefer local).
     */
    fun shouldRouteThroughLocalRelay(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        val isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                          capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        // If on Wi-Fi but NO validated internet -> Perfect scenario for local PocketBase relay
        return isWifi && !hasInternet
    }
}
