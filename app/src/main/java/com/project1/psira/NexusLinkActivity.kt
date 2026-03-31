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

    // Bluetooth Mesh (Carrier 1)
    private val PSIRA_UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")
    private val btAdapter: BluetoothAdapter? by lazy {
        (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }
    private var btAcceptThread: BtAcceptThread? = null
    private var btConnectThread: BtConnectThread? = null
    private var btDataThread: BtDataThread? = null

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
            // Request Discoverability
            val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)
            }
            startActivity(discoverableIntent)
            
            startBtDiscovery()
            startBtBroadcasting()
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
            btAdapter?.cancelDiscovery()
        } else {
            wifiManager.stopPeerDiscovery(wifiChannel, null)
        }
    }

    private fun handleSend() {
        val text = etMessage.text.toString()
        if (text.isNotEmpty()) {
            val encoded = PsiRaConverter.encode(text)
            if (currentMode == LinkMode.MESH) {
                btDataThread?.write(encoded.toByteArray())
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

    // --- Bluetooth Implementation ---

    private fun startBtBroadcasting() {
        if (btAdapter?.isEnabled == false) return
        btAcceptThread?.cancel()
        btAcceptThread = BtAcceptThread()
        btAcceptThread?.start()
    }

    private fun startBtDiscovery() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return
        btAdapter?.startDiscovery()
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(btReceiver, filter)
    }

    private val btReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (BluetoothDevice.ACTION_FOUND == intent.action) {
                val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                device?.let {
                    val name = it.name ?: "Unknown Node"
                    if (name.contains("PsiRa", ignoreCase = true)) {
                        tvStatus.text = "ELITE NODE DETECTED: $name"
                        vibrate(50)
                        connectToBtDevice(it)
                    } else {
                        // For manual testing/visibility
                        tvStatus.text = "SCANNING... FOUND: $name"
                    }
                }
            }
        }
    }

    private fun connectToBtDevice(device: BluetoothDevice) {
        btConnectThread?.cancel()
        btConnectThread = BtConnectThread(device)
        btConnectThread?.start()
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
        btAcceptThread?.cancel()
        btConnectThread?.cancel()
        btDataThread?.cancel()
        wfServerThread?.interrupt()
        wfClientThread?.interrupt()
        wfDataThread?.interrupt()
    }

    // --- Inner Threads (BT) ---
    private inner class BtAcceptThread : Thread() {
        private val mmServerSocket: BluetoothServerSocket? by lazy {
            if (ActivityCompat.checkSelfPermission(this@NexusLinkActivity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return@lazy null
            btAdapter?.listenUsingInsecureRfcommWithServiceRecord("SpectreMesh", PSIRA_UUID)
        }
        override fun run() {
            val socket = mmServerSocket?.accept()
            socket?.let { 
                onLinkEstablished()
                btDataThread = BtDataThread(it)
                btDataThread?.start()
            }
        }
        fun cancel() { try { mmServerSocket?.close() } catch (e: Exception) {} }
    }

    private inner class BtConnectThread(device: BluetoothDevice) : Thread() {
        private val mmSocket: BluetoothSocket? by lazy {
             if (ActivityCompat.checkSelfPermission(this@NexusLinkActivity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return@lazy null
            device.createInsecureRfcommSocketToServiceRecord(PSIRA_UUID)
        }
        override fun run() {
            try {
                mmSocket?.connect()
                onLinkEstablished()
                btDataThread = BtDataThread(mmSocket!!)
                btDataThread?.start()
            } catch (e: Exception) {
                handler.post { tvStatus.text = "LINK FAILED" }
            }
        }
        fun cancel() { try { mmSocket?.close() } catch (e: Exception) {} }
    }

    private inner class BtDataThread(private val socket: BluetoothSocket) : Thread() {
        private val mmInStream = socket.inputStream
        private val mmBuffer = ByteArray(1024)
        override fun run() {
            while (true) {
                val numBytes = try { mmInStream.read(mmBuffer) } catch (e: Exception) { break }
                val decoded = PsiRaConverter.decode(String(mmBuffer, 0, numBytes))
                postMessage(decoded)
            }
        }
        fun write(bytes: ByteArray) { try { socket.outputStream.write(bytes) } catch (e: Exception) {} }
        fun cancel() { try { socket.close() } catch (e: Exception) {} }
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
                postMessage(PsiRaConverter.decodeAny(received))
            }
        }
        fun write(data: String) { Thread { try { PrintWriter(socket.getOutputStream(), true).println(data) } catch (e: Exception) {} }.start() }
    }

    private fun postMessage(msg: String) {
        handler.post {
            messageList.add(SpectreMessage("Agent", msg, false))
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
        try { unregisterReceiver(btReceiver) } catch (e: Exception) {}
        try { unregisterReceiver(wfReceiver) } catch (e: Exception) {}
    }
}
