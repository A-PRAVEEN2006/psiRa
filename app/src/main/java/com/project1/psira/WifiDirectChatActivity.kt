package com.project1.psira

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.*
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

class WifiDirectChatActivity : BaseActivity() {

    private lateinit var manager: WifiP2pManager
    private lateinit var channel: WifiP2pManager.Channel
    private lateinit var receiver: BroadcastReceiver
    private val intentFilter = IntentFilter()

    private lateinit var rvMessages: RecyclerView
    private lateinit var adapter: BluetoothChatAdapter
    private val messageList = mutableListOf<BluetoothMessage>()
    private lateinit var tvStatus: TextView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: Button

    private var serverThread: ServerThread? = null
    private var clientThread: ClientThread? = null
    private var dataTransferThread: DataTransferThread? = null

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wifi_direct_chat)

        manager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel = manager.initialize(this, mainLooper, null)

        rvMessages = findViewById(R.id.rvGhostMessages)
        tvStatus = findViewById(R.id.ghostStatusStrip)
        etMessage = findViewById(R.id.etGhostMessage)
        btnSend = findViewById(R.id.btnGhostSend)

        adapter = BluetoothChatAdapter(messageList)
        rvMessages.layoutManager = LinearLayoutManager(this)
        rvMessages.adapter = adapter

        loadLocalArchive()

        findViewById<Button>(R.id.btnDiscoverGhost).setOnClickListener {
            startDiscovery()
        }

        btnSend.setOnClickListener {
            val text = etMessage.text.toString()
            if (text.isNotEmpty()) {
                sendMessage(text)
                etMessage.setText("")
                saveLocalArchive()
            }
        }

        setupIntentFilter()
        checkPermissions()
    }

    private fun loadLocalArchive() {
        val prefs = getSharedPreferences("PsiRaGhostArchive", Context.MODE_PRIVATE)
        val raw = prefs.getString("history", "") ?: ""
        if (raw.isNotEmpty()) {
            val items = raw.split("[MSG_SEP]")
            for (item in items) {
                val fields = item.split("|")
                if (fields.size == 3) {
                    messageList.add(BluetoothMessage(fields[0], fields[1], fields[2] == "1"))
                }
            }
            adapter.notifyDataSetChanged()
            rvMessages.scrollToPosition(messageList.size - 1)
        }
    }

    private fun saveLocalArchive() {
        val prefs = getSharedPreferences("PsiRaGhostArchive", Context.MODE_PRIVATE)
        val sb = StringBuilder()
        val start = if (messageList.size > 50) messageList.size - 50 else 0
        for (i in start until messageList.size) {
            val msg = messageList[i]
            sb.append("${msg.sender}|${msg.message}|${if (msg.isMe) "1" else "0"}")
            if (i < messageList.size - 1) sb.append("[MSG_SEP]")
        }
        prefs.edit().putString("history", sb.toString()).apply()
    }

    private fun setupIntentFilter() {
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 102)
    }

    private fun isWifiEnabled(): Boolean {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
        return wifiManager.isWifiEnabled
    }

    private fun startDiscovery() {
        if (!isWifiEnabled()) {
            Toast.makeText(this, "⚠ Wi-Fi must be ON for Ghost Network.", Toast.LENGTH_LONG).show()
            startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS))
            return
        }
        
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        if (!locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
            Toast.makeText(this, "⚠ Location/GPS must be ON for Node Discovery.", Toast.LENGTH_LONG).show()
            startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            return
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            checkPermissions()
            return
        }
        
        manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                tvStatus.text = "SIGNAL: DISCOVERY INITIATED"
                tvStatus.setTextColor(android.graphics.Color.YELLOW)
            }
            override fun onFailure(reason: Int) {
                val errorMsg = when (reason) {
                    WifiP2pManager.P2P_UNSUPPORTED -> "P2P UNSUPPORTED ON THIS DEVICE"
                    WifiP2pManager.BUSY -> "SYSTEM BUSY (RETRYING...)"
                    WifiP2pManager.ERROR -> "INTERNAL RADIO ERROR"
                    else -> "UNKNOWN FAILURE ($reason)"
                }
                tvStatus.text = "SIGNAL: $errorMsg"
                tvStatus.setTextColor(android.graphics.Color.RED)
                if (reason == WifiP2pManager.BUSY) {
                    handler.postDelayed({ startDiscovery() }, 2000)
                }
            }
        })
    }

    private val peerListListener = WifiP2pManager.PeerListListener { peerList ->
        val peers = peerList.deviceList.toList()
        if (peers.isNotEmpty()) {
            val options = peers.map { "${it.deviceName}\n[${it.deviceAddress}]" }
            PsiRaDialogs.showOptionsSheet(this, "GHOST SCAN RESULTS", options) { which ->
                connectToPeer(peers[which])
            }
        }
    }

    private fun connectToPeer(device: WifiP2pDevice) {
        val config = WifiP2pConfig()
        config.deviceAddress = device.deviceAddress
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        manager.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                tvStatus.text = "SIGNAL: HANDSHAKING..."
            }
            override fun onFailure(reason: Int) {
                tvStatus.text = "SIGNAL: HANDSHAKE FAILED"
            }
        })
    }

    private val connectionListener = WifiP2pManager.ConnectionInfoListener { info ->
        if (info.groupFormed && info.isGroupOwner) {
            tvStatus.text = "SIGNAL: NODE PRIMARY"
            serverThread?.interrupt()
            serverThread = ServerThread()
            serverThread?.start()
        } else if (info.groupFormed) {
            tvStatus.text = "SIGNAL: NODE SECONDARY"
            clientThread?.interrupt()
            clientThread = ClientThread(info.groupOwnerAddress.hostAddress!!)
            clientThread?.start()
        }
    }

    private fun manageSocket(socket: Socket) {
        handler.post {
            tvStatus.text = "SIGNAL: GHOST LINK ESTABLISHED"
            tvStatus.setTextColor(android.graphics.Color.GREEN)
            btnSend.isEnabled = true
        }
        dataTransferThread?.interrupt()
        dataTransferThread = DataTransferThread(socket)
        dataTransferThread?.start()
    }

    private fun sendMessage(text: String) {
        val encoded = PsiRaConverter.encode(text)
        dataTransferThread?.write(encoded)
        messageList.add(BluetoothMessage("You", text, true))
        adapter.notifyItemInserted(messageList.size - 1)
        rvMessages.scrollToPosition(messageList.size - 1)
    }

    private inner class ServerThread : Thread() {
        override fun run() {
            try {
                val serverSocket = ServerSocket(8888)
                val socket = serverSocket.accept()
                manageSocket(socket)
            } catch (e: Exception) {}
        }
    }

    private inner class ClientThread(val host: String) : Thread() {
        override fun run() {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(host, 8888), 5000)
                manageSocket(socket)
            } catch (e: Exception) {}
        }
    }

    private inner class DataTransferThread(val socket: Socket) : Thread() {
        private val inputStream = socket.getInputStream()
        private val outputStream = socket.getOutputStream()
        override fun run() {
            val reader = BufferedReader(InputStreamReader(inputStream))
            while (true) {
                try {
                    val received = reader.readLine() ?: break
                    val decoded = PsiRaConverter.decodeAny(received)
                    handler.post {
                        messageList.add(BluetoothMessage("Agent", decoded, false))
                        adapter.notifyItemInserted(messageList.size - 1)
                        rvMessages.scrollToPosition(messageList.size - 1)
                        saveLocalArchive()
                    }
                } catch (e: Exception) {
                    handler.post { tvStatus.text = "SIGNAL: LINK TERMINATED" }
                    break
                }
            }
        }
        fun write(data: String) {
            Thread {
                try {
                    val writer = PrintWriter(BufferedWriter(OutputStreamWriter(outputStream)), true)
                    writer.println(data)
                } catch (e: Exception) {}
            }.start()
        }
    }

    override fun onResume() {
        super.onResume()
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> manager.requestPeers(channel, peerListListener)
                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> manager.requestConnectionInfo(channel, connectionListener)
                }
            }
        }
        registerReceiver(receiver, intentFilter)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(receiver)
    }
}
