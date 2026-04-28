package com.project1.psira

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class SpectreNodeService : Service() {

    companion object {
        const val CHANNEL_ID = "SpectreNodeChannel"
        const val NOTIFICATION_ID = 1001
        const val SERVICE_ACTION_START = "com.project1.psira.START_SPECTRE_NODE"
        const val SERVICE_ACTION_STOP = "com.project1.psira.STOP_SPECTRE_NODE"
        const val BROADCAST_SPECTRE_MESSAGE = "com.project1.psira.SPECTRE_MESSAGE"
        const val BROADCAST_SPECTRE_STATUS = "com.project1.psira.SPECTRE_STATUS"
        const val ACTION_SEND_MESSAGE = "com.project1.psira.SEND_MESSAGE"
        
        val PSIRA_UUID: UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")
    }

    private var btAdapter: BluetoothAdapter? = null
    private var isRunning = false
    private val connectedSockets = mutableListOf<BluetoothSocket>()
    private val seenMessageIds = mutableSetOf<String>()
    
    private var acceptThread: AcceptThread? = null
    private lateinit var deadDropManager: DeadDropRelayManager

    override fun onCreate() {
        super.onCreate()
        deadDropManager = DeadDropRelayManager(this)
        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        btAdapter = btManager.adapter
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            SERVICE_ACTION_START -> {
                if (!isRunning) {
                    startForeground(NOTIFICATION_ID, buildNotification())
                    isRunning = true
                    startMeshServer()
                    startAutoDiscovery()
                }
            }
            SERVICE_ACTION_STOP -> {
                stopMesh()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_SEND_MESSAGE -> {
                val msg = intent.getStringExtra("message") ?: return START_STICKY
                val alias = intent.getStringExtra("alias") ?: "Unknown Agent"
                val targetId = intent.getStringExtra("target") ?: "ALL"
                val packetId = UUID.randomUUID().toString()
                
                // UUID|TTL|TargetID|Alias|Message
                val packet = "$packetId|5|$targetId|$alias|$msg"
                seenMessageIds.add(packetId)
                broadcastToMesh(packet, null)
            }
        }
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Spectre Node Active 🕷️")
            .setContentText("Silent Multi-Hop Routing Enabled")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setColor(0xFF1744.toInt()) // Red
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Spectre Sleeper Network",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun startMeshServer() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return
        acceptThread = AcceptThread()
        acceptThread?.start()
    }

    private fun startAutoDiscovery() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(discoveryReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(discoveryReceiver, filter)
        }
        btAdapter?.startDiscovery()
    }

    private val activeConnectionAttempts = mutableSetOf<String>()

    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (BluetoothDevice.ACTION_FOUND == intent.action) {
                val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return
                
                device?.let {
                    val address = it.address
                    if (activeConnectionAttempts.contains(address)) return
                    
                    val name = it.name ?: "Unknown Node"
                    if (name.contains("PsiRa", ignoreCase = true) || name.contains("Spectre", ignoreCase = true)) {
                        updateStatus("ELITE NODE DETECTED: $name")
                        // Attempt connection
                        activeConnectionAttempts.add(address)
                        ConnectThread(it).start()
                    } else {
                        updateStatus("SCANNING... FOUND: $name")
                    }
                }
            }
        }
    }

    private fun updateStatus(status: String) {
        val uiIntent = Intent(BROADCAST_SPECTRE_STATUS)
        uiIntent.putExtra("status", status)
        sendBroadcast(uiIntent)
    }

    // --- Mesh Networking Logic ---

    private fun manageConnectedSocket(socket: BluetoothSocket) {
        synchronized(connectedSockets) {
            connectedSockets.add(socket)
            updateStatus("LINKED")
        }
        DataThread(socket).start()

        // Sync Dead Drop vault with new node
        val stored = deadDropManager.getAllStoredPayloads()
        for (packet in stored) {
            sendDirectToSocket(packet, socket)
        }
    }

    private fun sendDirectToSocket(packet: String, socket: BluetoothSocket) {
        val bytes = "$packet\n".toByteArray()
        try {
            socket.outputStream.write(bytes)
        } catch (e: Exception) {
            Log.e("SpectreNode", "Failed to sync dead drop payload to node")
        }
    }

    private fun broadcastToMesh(packet: String, excludeSocket: BluetoothSocket?) {
        val bytes = "$packet\n".toByteArray() // Add newline for stream delimitation
        synchronized(connectedSockets) {
            val iterator = connectedSockets.iterator()
            while (iterator.hasNext()) {
                val socket = iterator.next()
                if (socket != excludeSocket) {
                    try {
                        socket.outputStream.write(bytes)
                    } catch (_: Exception) {
                        try { socket.close() } catch (_: Exception) {}
                        iterator.remove()
                    }
                }
            }
        }
    }

    private fun processIncomingPacket(packet: String, sourceSocket: BluetoothSocket) {
        // Format: UUID|TTL|TargetID|Alias|Message
        val parts = packet.split("|")
        if (parts.size >= 5) {
            val uuid = parts[0]
            val ttl = parts[1].toIntOrNull() ?: 0
            val targetId = parts[2]
            val alias = parts[3]
            val msg = parts.subList(4, parts.size).joinToString("|")

            if (!seenMessageIds.contains(uuid)) {
                seenMessageIds.add(uuid)
                deadDropManager.storePayload(packet)
                
                val myAlias = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.displayName ?: "Unknown"

                // Display only if targeted to me or broadcast
                if (targetId == "ALL" || targetId.equals(myAlias, ignoreCase = true)) {
                    // Send to local UI
                    val uiIntent = Intent(BROADCAST_SPECTRE_MESSAGE)
                    uiIntent.putExtra("alias", alias)
                    uiIntent.putExtra("message", msg)
                    sendBroadcast(uiIntent)
                }

                // Rebroadcast if TTL > 0
                if (ttl > 0) {
                    val newTtl = ttl - 1
                    val newPacket = "$uuid|$newTtl|$targetId|$alias|$msg"
                    broadcastToMesh(newPacket, sourceSocket)
                }
            }
        }
    }

    private inner class AcceptThread : Thread() {
        private val mmServerSocket: BluetoothServerSocket? by lazy {
            if (ActivityCompat.checkSelfPermission(this@SpectreNodeService, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return@lazy null
            btAdapter?.listenUsingInsecureRfcommWithServiceRecord("SpectreMesh", PSIRA_UUID)
        }
        override fun run() {
            var shouldLoop = true
            while (shouldLoop && isRunning && !isInterrupted) {
                val socket: BluetoothSocket? = try {
                    mmServerSocket?.accept()
                } catch (_: Exception) {
                    shouldLoop = false
                    null
                }
                socket?.also {
                    manageConnectedSocket(it)
                }
            }
        }
        fun cancel() {
            try { interrupt(); mmServerSocket?.close() } catch (_: Exception) {}
        }
    }

    private inner class ConnectThread(val device: BluetoothDevice) : Thread() {
        private val mmSocket: BluetoothSocket? by lazy {
            if (ActivityCompat.checkSelfPermission(this@SpectreNodeService, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return@lazy null
            device.createInsecureRfcommSocketToServiceRecord(PSIRA_UUID)
        }
        override fun run() {
            if (ActivityCompat.checkSelfPermission(this@SpectreNodeService, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                btAdapter?.cancelDiscovery()
            }
            try {
                if (isInterrupted) return
                mmSocket?.connect()
                mmSocket?.let { manageConnectedSocket(it) }
            } catch (_: Exception) {
                try { mmSocket?.close() } catch (_: Exception) {}
            } finally {
                activeConnectionAttempts.remove(device.address)
            }
        }
        fun cancel() {
            try { interrupt(); mmSocket?.close() } catch (_: Exception) {}
        }
    }

    private inner class DataThread(private val mmSocket: BluetoothSocket) : Thread() {
        private val mmInStream: InputStream = mmSocket.inputStream
        private val mmOutStream: OutputStream = mmSocket.outputStream

        override fun run() {
            val reader = mmInStream.bufferedReader()
            while (isRunning && !isInterrupted) {
                try {
                    val line = reader.readLine() ?: break
                    processIncomingPacket(line, mmSocket)
                } catch (e: Exception) {
                    synchronized(connectedSockets) { 
                        connectedSockets.remove(mmSocket) 
                        if (connectedSockets.isEmpty()) {
                            updateStatus("UNLINKED")
                        }
                    }
                    try { mmSocket.close() } catch (_: Exception) {}
                    break
                }
            }
        }
    }


    private fun stopMesh() {
        isRunning = false
        try { unregisterReceiver(discoveryReceiver) } catch (e: Exception) {}
        acceptThread?.cancel()
        synchronized(connectedSockets) {
            for (socket in connectedSockets) {
                try { socket.close() } catch (e: Exception) {}
            }
            connectedSockets.clear()
        }
    }

    override fun onDestroy() {
        stopMesh()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? = null
}
