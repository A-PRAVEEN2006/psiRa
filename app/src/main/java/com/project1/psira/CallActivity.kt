package com.project1.psira

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class CallActivity : AppCompatActivity() {

    private val db = FirebaseDatabase.getInstance()
    private val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    private var targetUid = ""
    private var callerUid = ""
    private var callMode = "OUTGOING"
    private var isCallActive = false   // starts false; true only after permission + init
    private var isMuted = false
    private var isSpeakerOn = false
    private var targetName = "Agent"
    private var callConnectedTime = 0L

    private var rtcClient: WebRTCClient? = null
    private lateinit var tvStatus: TextView
    private lateinit var audioManager: android.media.AudioManager
    private lateinit var btnAccept: ImageButton
    private lateinit var btnEnd: ImageButton
    private lateinit var btnMute: ImageButton
    private lateinit var btnSpeaker: ImageButton

    companion object {
        private const val REQUEST_MICROPHONE = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call)

        targetName = intent.getStringExtra("TARGET_NAME") ?: "Unknown Agent"
        targetUid = intent.getStringExtra("TARGET_UID") ?: ""
        callerUid = intent.getStringExtra("CALLER_UID") ?: ""
        callMode = intent.getStringExtra("CALL_MODE") ?: "OUTGOING"

        // Bind UI
        findViewById<TextView>(R.id.tvCallerName).text = targetName
        findViewById<TextView>(R.id.tvCallerInitial).text = targetName.take(1).uppercase()
        btnAccept = findViewById(R.id.btnAcceptCall)
        btnEnd = findViewById(R.id.btnEndCall)
        tvStatus = findViewById(R.id.tvCallStatus)
        btnMute = findViewById(R.id.btnMute)
        btnSpeaker = findViewById(R.id.btnSpeaker)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager

        // ── Step 1: Check permission BEFORE starting anything ──
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            // Permission already granted — start call immediately
            initCallSession()
        } else {
            // Show a waiting state and request permission
            tvStatus.text = "Requesting Microphone Access..."
            btnAccept.visibility = View.GONE
            btnEnd.setOnClickListener { terminateCall() }
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.RECORD_AUDIO),
                REQUEST_MICROPHONE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MICROPHONE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission just granted — now safe to start
                initCallSession()
            } else {
                Toast.makeText(this, "Microphone permission required for secure voice.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    /**
     * Called only after microphone permission is confirmed.
     * Starts the foreground service and WebRTC safely.
     */
    private fun initCallSession() {
        isCallActive = true

        // Audio routing
        audioManager.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
        audioManager.isMicrophoneMute = false
        audioManager.isSpeakerphoneOn = false

        // Wire notification controls → activity
        CallServiceBridge.endCallCallback = { runOnUiThread { terminateCall() } }
        CallServiceBridge.muteCallback = { muted ->
            isMuted = muted
            rtcClient?.toggleMute(isMuted)
            runOnUiThread {
                if (isMuted) btnMute.setColorFilter(android.graphics.Color.RED)
                else btnMute.clearColorFilter()
            }
        }

        // Start foreground service — safe because permission is now granted
        CallService.start(this, targetName)

        // WebRTC client
        rtcClient = WebRTCClient(
            this, myUid,
            if (callMode == "OUTGOING") targetUid else callerUid,
            object : WebRTCClient.WebRTCListener {
                override fun onCallReady() {
                    runOnUiThread {
                        tvStatus.text = "SECURE CHANNEL ESTABLISHED"
                        tvStatus.setTextColor(android.graphics.Color.GREEN)
                        callConnectedTime = System.currentTimeMillis()
                    }
                }
                override fun onCallEnded() {
                    runOnUiThread {
                        tvStatus.text = "Connection Terminated"
                        android.os.Handler(android.os.Looper.getMainLooper())
                            .postDelayed({ terminateCall() }, 1000)
                    }
                }
            }
        )

        if (callMode == "OUTGOING") {
            tvStatus.text = "Initiating Secure Uplink..."
            btnAccept.visibility = View.GONE

            var hasStarted = false
            db.getReference("calls").child(targetUid)
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (!isCallActive) return
                        val status = snapshot.child("status").getValue(String::class.java)
                        when {
                            status == "accepted" && !hasStarted -> {
                                hasStarted = true
                                tvStatus.text = "Syncing Node..."
                                rtcClient?.startCall()
                            }
                            status == "rejected" || !snapshot.exists() -> {
                                tvStatus.text = "Call Rejected / Unavailable"
                                android.os.Handler(android.os.Looper.getMainLooper())
                                    .postDelayed({ terminateCall() }, 1500)
                            }
                        }
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })

            btnEnd.setOnClickListener { terminateCall() }

        } else {
            // INCOMING
            val alreadyAccepted = intent.getBooleanExtra("ALREADY_ACCEPTED", false)
            val myCallRef = db.getReference("calls").child(myUid)

            if (alreadyAccepted) {
                // User already tapped Accept in the BottomSheet — go straight to connecting
                tvStatus.text = "Establishing Secure Link..."
                btnAccept.visibility = View.GONE
                rtcClient?.acceptCall()
            } else {
                // User arrived here without accepting (e.g. direct call)
                tvStatus.text = "Secure Signal Incoming..."
                btnAccept.visibility = View.VISIBLE
                btnAccept.setOnClickListener {
                    myCallRef.child("status").setValue("accepted")
                    tvStatus.text = "Connecting..."
                    btnAccept.visibility = View.GONE
                    rtcClient?.acceptCall()
                }
            }

            // If caller cancels or call node disappears — auto-close
            myCallRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!isCallActive) return
                    if (!snapshot.exists()) terminateCall()
                }
                override fun onCancelled(error: DatabaseError) {}
            })

            btnEnd.setOnClickListener { terminateCall() }
        }

        // Mute toggle
        btnMute.setOnClickListener {
            isMuted = !isMuted
            rtcClient?.toggleMute(isMuted)
            if (isMuted) btnMute.setColorFilter(android.graphics.Color.RED) else btnMute.clearColorFilter()
            Toast.makeText(this, if (isMuted) "Mic Muted" else "Mic Active", Toast.LENGTH_SHORT).show()
        }

        // Speaker toggle
        btnSpeaker.setOnClickListener {
            isSpeakerOn = !isSpeakerOn
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val devices = audioManager.availableCommunicationDevices
                val speaker = devices.find { it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                if (isSpeakerOn && speaker != null) audioManager.setCommunicationDevice(speaker)
                else audioManager.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = isSpeakerOn
            }
            if (isSpeakerOn) btnSpeaker.setColorFilter(android.graphics.Color.GREEN) else btnSpeaker.clearColorFilter()
            Toast.makeText(this, if (isSpeakerOn) "Speaker ON" else "Handset Mode", Toast.LENGTH_SHORT).show()
        }
    }

    private fun terminateCall() {
        if (!isCallActive) return
        isCallActive = false

        val timestamp = System.currentTimeMillis()
        val duration = if (callConnectedTime > 0L) {
            (System.currentTimeMillis() - callConnectedTime) / 1000
        } else {
            0L
        }
        val type = if (callConnectedTime > 0L) {
            callMode
        } else {
            if (callMode == "OUTGOING") "OUTGOING" else "MISSED"
        }
        val logUid = if (callMode == "OUTGOING") targetUid else callerUid
        CallHistoryActivity.saveCallLog(this, targetName, logUid, timestamp, duration, type)

        audioManager.mode = android.media.AudioManager.MODE_NORMAL
        val nodeUid = if (callMode == "OUTGOING") targetUid else myUid
        db.getReference("calls").child(nodeUid).removeValue()
        rtcClient?.close()
        CallService.stop(this)
        CallServiceBridge.endCallCallback = null
        CallServiceBridge.muteCallback = null
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isCallActive) {
            terminateCall()
        } else {
            rtcClient?.close()
        }
    }
}
