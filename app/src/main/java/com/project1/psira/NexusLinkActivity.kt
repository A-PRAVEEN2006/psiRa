package com.project1.psira

import android.Manifest
import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.p2p.*
import android.graphics.Color
import android.os.*
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.*
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.*

class NexusLinkActivity : BaseActivity() {

    enum class LinkMode { MESH, GHOST }
    private var currentMode = LinkMode.MESH

    // Common UI
    private lateinit var rvMessages: RecyclerView
    private lateinit var adapter: SpectreChatAdapter
    private val messageList = mutableListOf<SpectreMessage>()
    private lateinit var radarView: RadarPulseView
    private lateinit var tvStatus: TextView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var btnScan: Button
    private lateinit var btnModeMesh: TextView
    private lateinit var btnModeGhost: TextView
    private lateinit var etTargetId: EditText

    // Ghost Network (Carrier 2)
    private lateinit var wifiManager: WifiP2pManager
    private lateinit var wifiChannel: WifiP2pManager.Channel
    private var wfServerThread: WfServerThread? = null
    private var wfClientThread: WfClientThread? = null
    private var wfDataThread: WfDataThread? = null

    private val handler = Handler(Looper.getMainLooper())
    private var isScanning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_proximity_unified)

        initUI()
        initCarriers()
        loadArchive()
        
        
        val filter = IntentFilter(SpectreNodeService.BROADCAST_SPECTRE_MESSAGE)
        val statusFilter = IntentFilter(SpectreNodeService.BROADCAST_SPECTRE_STATUS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(spectreReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(statusReceiver, statusFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(spectreReceiver, filter)
            registerReceiver(statusReceiver, statusFilter)
        }
    }

    private fun initUI() {
        rvMessages = findViewById(R.id.rvNexusMessages)
        radarView = findViewById(R.id.radarView)
        tvStatus = findViewById(R.id.tvDiscoveryHint)
        etMessage = findViewById(R.id.etNexusMessage)
        btnSend = findViewById(R.id.btnNexusSend)
        btnScan = findViewById(R.id.btnInitiateScan)
        btnModeMesh = findViewById(R.id.btnModeMesh)
        btnModeGhost = findViewById(R.id.btnModeGhost)
        etTargetId = findViewById(R.id.etTargetId)

        adapter = SpectreChatAdapter(messageList)
        rvMessages.layoutManager = LinearLayoutManager(this)
        rvMessages.adapter = adapter

        btnModeMesh.setOnClickListener { switchMode(LinkMode.MESH) }
        btnModeGhost.setOnClickListener { switchMode(LinkMode.GHOST) }

        btnScan.setOnClickListener { handleScan() }
        btnSend.setOnClickListener { handleSend() }
    }

    private fun initCarriers() {
        // Wi-Fi Setup
        wifiManager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        wifiChannel = wifiManager.initialize(this, mainLooper, null)
    }

    private fun switchMode(mode: LinkMode) {
        if (isScanning || currentMode == mode) return
        currentMode = mode
        
        // Reset UI colors
        if (mode == LinkMode.MESH) {
            btnModeMesh.setBackgroundResource(R.drawable.bg_rounded_primary)
            btnModeMesh.setTextColor(Color.BLACK)
            btnModeGhost.setBackground(null)
            btnModeGhost.setTextColor(Color.GRAY)
            tvStatus.text = "SPECTRE MESH (OFFLINE)"
        } else {
            btnModeGhost.setBackgroundResource(R.drawable.bg_rounded_primary)
            btnModeGhost.setTextColor(Color.BLACK)
            btnModeMesh.setBackground(null)
            btnModeMesh.setTextColor(Color.GRAY)
            tvStatus.text = "SPECTRE GHOST (WI-FI)"
        }
        
        terminateAllThreads()
        btnSend.isEnabled = false
        btnScan.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.psira_accent)))
    }

    private fun handleScan() {
        if (isScanning) {
            stopScan()
        } else {
            startScan()
        }
    }

    private fun startScan() {
        isScanning = true
        radarView.start()
        btnScan.text = "TERMINATE SCAN"
        btnScan.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.RED))
        
        if (currentMode == LinkMode.MESH) {
            val intent = Intent(this, SpectreNodeService::class.java)
            intent.action = SpectreNodeService.SERVICE_ACTION_START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            tvStatus.text = "SPECTRE BACKGROUND MESH ACTIVE"
            tvStatus.setTextColor(Color.GREEN)
        } else {
            startWfDiscovery()
        }
    }

    private fun stopScan() {
        isScanning = false
        radarView.stop()
        btnScan.text = "INITIATE TACTICAL SCAN"
        btnScan.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.psira_accent)))
        
        if (currentMode == LinkMode.MESH) {
            val intent = Intent(this, SpectreNodeService::class.java)
            intent.action = SpectreNodeService.SERVICE_ACTION_STOP
            startService(intent)
            tvStatus.text = "SPECTRE MESH (OFFLINE)"
            tvStatus.setTextColor(Color.GRAY)
        } else {
            wifiManager.stopPeerDiscovery(wifiChannel, null)
        }
    }

    private fun handleSend() {
        val text = etMessage.text.toString()
        if (text.isNotEmpty()) {
            val encoded = PsiRaConverter.encode(text)
            if (currentMode == LinkMode.MESH) {
                val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                val targetId = etTargetId.text.toString().trim()
                val finalTarget = if (targetId.isEmpty()) "ALL" else targetId
                
                val intent = Intent(this, SpectreNodeService::class.java).apply {
                    action = SpectreNodeService.ACTION_SEND_MESSAGE
                    putExtra("message", text)
                    putExtra("alias", user?.displayName ?: "Ghost")
                    putExtra("target", finalTarget)
                }
                startService(intent)
            } else {
                wfDataThread?.write(encoded)
            }
            
            messageList.add(SpectreMessage("You", text, true))
            adapter.notifyItemInserted(messageList.size - 1)
            rvMessages.scrollToPosition(messageList.size - 1)
            etMessage.setText("")
            saveArchive()
        }
    }

    private val spectreReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == SpectreNodeService.BROADCAST_SPECTRE_MESSAGE) {
                val alias = intent.getStringExtra("alias") ?: "Unknown"
                val msg = intent.getStringExtra("message") ?: ""
                postMessage(alias, msg)
            }
        }
    }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == SpectreNodeService.BROADCAST_SPECTRE_STATUS) {
                val status = intent.getStringExtra("status") ?: ""
                if (status == "LINKED") {
                    tvStatus.text = "✔ SECURE NEXUS LINK ESTABLISHED"
                    tvStatus.setTextColor(Color.GREEN)
                    btnSend.isEnabled = true
                    vibrate(100)
                } else if (status == "UNLINKED") {
                    if (currentMode == LinkMode.MESH) {
                        tvStatus.text = "SPECTRE BACKGROUND MESH ACTIVE (0 NODES)"
                        tvStatus.setTextColor(Color.YELLOW)
                        btnSend.isEnabled = false
                    }
                } else if (status.startsWith("ELITE NODE DETECTED")) {
                    tvStatus.text = status
                    vibrate(50)
                } else {
                    tvStatus.text = status
                }
            }
        }
    }

    // --- Wi-Fi Implementation ---

    private fun startWfDiscovery() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        wifiManager.discoverPeers(wifiChannel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                tvStatus.text = "GHOST SIGNAL INITIATED..."
            }
            override fun onFailure(reason: Int) {}
        })
        val filter = IntentFilter()
        filter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        filter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        registerReceiver(wfReceiver, filter)
    }

    private val wfReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    wifiManager.requestPeers(wifiChannel) { peers ->
                        val list = peers.deviceList.toList()
                        if (list.isNotEmpty()) {
                            val options = list.map { "${it.deviceName}\n[${it.deviceAddress}]" }
                            PsiRaDialogs.showOptionsSheet(this@NexusLinkActivity, "DETECTED NODES", options) { which ->
                                connectToWfPeer(list[which])
                            }
                        }
                    }
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    wifiManager.requestConnectionInfo(wifiChannel) { info ->
                        if (info.groupFormed && info.isGroupOwner) {
                            wfServerThread = WfServerThread()
                            wfServerThread?.start()
                        } else if (info.groupFormed) {
                            wfClientThread = WfClientThread(info.groupOwnerAddress.hostAddress!!)
                            wfClientThread?.start()
                        }
                    }
                }
            }
        }
    }

    private fun connectToWfPeer(device: WifiP2pDevice) {
        val config = WifiP2pConfig().apply { deviceAddress = device.deviceAddress }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        wifiManager.connect(wifiChannel, config, null)
    }

    // --- Connection Management ---

    private fun onLinkEstablished() {
        handler.post {
            tvStatus.text = "✔ SECURE NEXUS LINK ESTABLISHED"
            tvStatus.setTextColor(Color.GREEN)
            btnSend.isEnabled = true
            stopScan()
            vibrate(100)
        }
    }

    private fun terminateAllThreads() {
        wfServerThread?.interrupt()
        wfClientThread?.interrupt()
        wfDataThread?.interrupt()
    }

    // --- Inner Threads (Wi-Fi) ---
    private inner class WfServerThread : Thread() {
        override fun run() {
            try {
                val socket = ServerSocket(8888).accept()
                onLinkEstablished()
                wfDataThread = WfDataThread(socket)
                wfDataThread?.start()
            } catch (e: Exception) {}
        }
    }

    private inner class WfClientThread(val host: String) : Thread() {
        override fun run() {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(host, 8888), 5000)
                onLinkEstablished()
                wfDataThread = WfDataThread(socket)
                wfDataThread?.start()
            } catch (e: Exception) {}
        }
    }

    private inner class WfDataThread(val socket: Socket) : Thread() {
        override fun run() {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            while (true) {
                val received = try { reader.readLine() ?: break } catch (e: Exception) { break }
                postMessage("Ghost Agent", PsiRaConverter.decodeAny(received))
            }
        }
        fun write(data: String) { Thread { try { PrintWriter(socket.getOutputStream(), true).println(data) } catch (e: Exception) {} }.start() }
    }

    private fun postMessage(sender: String, msg: String) {
        handler.post {
            messageList.add(SpectreMessage(sender, msg, false))
            adapter.notifyItemInserted(messageList.size - 1)
            rvMessages.scrollToPosition(messageList.size - 1)
            saveArchive()
        }
    }

    // --- Archiving ---
    private fun loadArchive() {
        val raw = getSharedPreferences("PsiRaNexus", Context.MODE_PRIVATE).getString("history", "") ?: ""
        if (raw.isNotEmpty()) {
            raw.split("[MSG_SEP]").forEach {
                val f = it.split("|")
                if (f.size == 3) messageList.add(SpectreMessage(f[0], f[1], f[2] == "1"))
            }
            adapter.notifyDataSetChanged()
            rvMessages.scrollToPosition(messageList.size - 1)
        }
    }

    private fun saveArchive() {
        val sb = StringBuilder()
        val start = Math.max(0, messageList.size - 50)
        for (i in start until messageList.size) {
            val m = messageList[i]
            sb.append("${m.sender}|${m.message}|${if (m.isMe) "1" else "0"}")
            if (i < messageList.size - 1) sb.append("[MSG_SEP]")
        }
        getSharedPreferences("PsiRaNexus", Context.MODE_PRIVATE).edit().putString("history", sb.toString()).apply()
    }


    override fun onDestroy() {
        super.onDestroy()
        terminateAllThreads()
        try { unregisterReceiver(spectreReceiver) } catch (e: Exception) {}
        try { unregisterReceiver(statusReceiver) } catch (e: Exception) {}
        try { unregisterReceiver(wfReceiver) } catch (e: Exception) {}
    }
}
