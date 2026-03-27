package com.project1.psira

import android.Manifest
import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

class BluetoothChatActivity : BaseActivity() {

    private val PSIRA_UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")
    private val btAdapter: BluetoothAdapter? by lazy {
        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        manager.adapter
    }

    private lateinit var rvMessages: RecyclerView
    private lateinit var adapter: BluetoothChatAdapter
    private val messageList = mutableListOf<BluetoothMessage>()
    private lateinit var tvStatus: TextView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: Button

    private var connectedThread: ConnectedThread? = null
    private var acceptThread: AcceptThread? = null
    private var connectThread: ConnectThread? = null

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bluetooth_chat)

        rvMessages = findViewById(R.id.rvBluetoothMessages)
        tvStatus = findViewById(R.id.tvBTStatus)
        etMessage = findViewById(R.id.etBTMessage)
        btnSend = findViewById(R.id.btnBTSend)

        adapter = BluetoothChatAdapter(messageList)
        rvMessages.layoutManager = LinearLayoutManager(this)
        rvMessages.adapter = adapter

        loadLocalArchive()

        findViewById<Button>(R.id.btnAdvertise).setOnClickListener { 
            if (btAdapter?.isEnabled == false) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivity(enableBtIntent)
            } else {
                startAdvertising() 
            }
        }
        findViewById<Button>(R.id.btnDiscover).setOnClickListener { 
             if (btAdapter?.isEnabled == false) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivity(enableBtIntent)
            } else {
                startDiscovery() 
            }
        }

        btnSend.setOnClickListener {
            val text = etMessage.text.toString()
            if (text.isNotEmpty()) {
                sendMessage(text)
                etMessage.setText("")
                saveLocalArchive()
            }
        }

        checkPermissions()
    }

    private fun loadLocalArchive() {
        val prefs = getSharedPreferences("PsiRaBTArchive", Context.MODE_PRIVATE)
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
        val prefs = getSharedPreferences("PsiRaBTArchive", Context.MODE_PRIVATE)
        val sb = StringBuilder()
        val start = if (messageList.size > 50) messageList.size - 50 else 0
        for (i in start until messageList.size) {
            val msg = messageList[i]
            sb.append("${msg.sender}|${msg.message}|${if (msg.isMe) "1" else "0"}")
            if (i < messageList.size - 1) sb.append("[MSG_SEP]")
        }
        prefs.edit().putString("history", sb.toString()).apply()
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 101)
        }
    }

    private var originalBtName: String? = null

    private fun startAdvertising() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            if (originalBtName == null) originalBtName = btAdapter?.name
            btAdapter?.name = "PsiRa-Agent"
        }

        val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
        }
        startActivity(discoverableIntent)

        acceptThread?.cancel()
        acceptThread = AcceptThread()
        acceptThread?.start()
        tvStatus.text = "STATUS: BROADCASTING SIGNATURE (PsiRa-Agent)..."
        tvStatus.setTextColor(android.graphics.Color.YELLOW)
    }

    private val discoveredDevices = mutableListOf<BluetoothDevice>()
    private lateinit var deviceAdapter: DeviceAdapter

    private fun startDiscovery() {
        if (!isLocationEnabled()) {
            Toast.makeText(this, "⚠ Location Services are OFF. Discovery will fail.", Toast.LENGTH_LONG).show()
            startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            return
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            checkPermissions()
            return
        }
        
        discoveredDevices.clear()
        btAdapter?.startDiscovery()
        
        val filter = IntentFilter()
        filter.addAction(BluetoothDevice.ACTION_FOUND)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        registerReceiver(receiver, filter)
        
        tvStatus.text = "STATUS: SCANNING NODES..."
        tvStatus.setTextColor(android.graphics.Color.CYAN)
        showDiscoveryDialog()
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        return locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
               locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
    }

    private fun showDiscoveryDialog() {
        val rvDevices = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@BluetoothChatActivity)
        }
        deviceAdapter = DeviceAdapter(discoveredDevices) { device ->
            connectToDevice(device)
            btAdapter?.cancelDiscovery()
        }
        rvDevices.adapter = deviceAdapter

        PsiRaDialogs.showDeleteSheet(this, "SELECT AGENT NODE", "Scanning for nearby Tactical Mesh signatures...", "CANCEL SCAN", rvDevices) {
            btAdapter?.cancelDiscovery()
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action: String = intent.action!!
            when (action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    device?.let {
                        if (!discoveredDevices.contains(it)) {
                            discoveredDevices.add(it)
                            if (::deviceAdapter.isInitialized) {
                                deviceAdapter.notifyDataSetChanged()
                            }
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    if (tvStatus.text.contains("SCANNING")) {
                        tvStatus.text = "STATUS: SCAN COMPLETE"
                    }
                }
            }
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        connectThread?.cancel()
        connectThread = ConnectThread(device)
        connectThread?.start()
    }

    private fun manageConnectedSocket(socket: BluetoothSocket) {
        tvStatus.text = "STATUS: MESH LINK ESTABLISHED"
        tvStatus.setTextColor(android.graphics.Color.GREEN)
        handler.post { btnSend.isEnabled = true }

        connectedThread?.cancel()
        connectedThread = ConnectedThread(socket)
        connectedThread?.start()
    }

    private fun sendMessage(text: String) {
        val encoded = PsiRaConverter.encode(text)
        connectedThread?.write(encoded.toByteArray())
        
        messageList.add(BluetoothMessage("You", text, true))
        adapter.notifyItemInserted(messageList.size - 1)
        rvMessages.scrollToPosition(messageList.size - 1)
    }

    private inner class AcceptThread : Thread() {
        private val mmServerSocket: BluetoothServerSocket? by lazy(LazyThreadSafetyMode.NONE) {
            if (ActivityCompat.checkSelfPermission(this@BluetoothChatActivity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return@lazy null
            btAdapter?.listenUsingInsecureRfcommWithServiceRecord("PsiRaMesh", PSIRA_UUID)
        }

        override fun run() {
            var shouldLoop = true
            while (shouldLoop) {
                val socket: BluetoothSocket? = try {
                    mmServerSocket?.accept()
                } catch (e: IOException) {
                    shouldLoop = false
                    null
                }
                socket?.let {
                    manageConnectedSocket(it)
                    mmServerSocket?.close()
                    shouldLoop = false
                }
            }
        }

        fun cancel() {
            try {
                mmServerSocket?.close()
            } catch (e: IOException) {}
        }
    }

    private inner class ConnectThread(device: BluetoothDevice) : Thread() {
        private val mmSocket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
            if (ActivityCompat.checkSelfPermission(this@BluetoothChatActivity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return@lazy null
            device.createInsecureRfcommSocketToServiceRecord(PSIRA_UUID)
        }

        override fun run() {
            btAdapter?.cancelDiscovery()
            try {
                mmSocket?.connect()
            } catch (e: IOException) {
                handler.post { tvStatus.text = "STATUS: CONNECTION FAILED" }
                return
            }
            mmSocket?.let { manageConnectedSocket(it) }
        }

        fun cancel() {
            try {
                mmSocket?.close()
            } catch (e: IOException) {}
        }
    }

    private inner class ConnectedThread(private val mmSocket: BluetoothSocket) : Thread() {
        private val mmInStream: InputStream = mmSocket.inputStream
        private val mmOutStream: OutputStream = mmSocket.outputStream
        private val mmBuffer: ByteArray = ByteArray(1024)

        override fun run() {
            var numBytes: Int
            while (true) {
                numBytes = try {
                    mmInStream.read(mmBuffer)
                } catch (e: IOException) {
                    handler.post { tvStatus.text = "STATUS: LINK TERMINATED" }
                    break
                }
                val received = String(mmBuffer, 0, numBytes)
                val decoded = PsiRaConverter.decode(received)
                handler.post {
                    messageList.add(BluetoothMessage("Agent", decoded, false))
                    adapter.notifyItemInserted(messageList.size - 1)
                    rvMessages.scrollToPosition(messageList.size - 1)
                    saveLocalArchive()
                }
            }
        }

        fun write(bytes: ByteArray) {
            try {
                mmOutStream.write(bytes)
            } catch (e: IOException) {}
        }

        fun cancel() {
            try {
                mmSocket.close()
            } catch (e: IOException) {}
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            originalBtName?.let { btAdapter?.name = it }
        }
        acceptThread?.cancel()
        connectThread?.cancel()
        connectedThread?.cancel()
        try { unregisterReceiver(receiver) } catch (e: Exception) {}
    }
}
