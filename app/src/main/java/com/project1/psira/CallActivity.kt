package com.project1.psira

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class CallActivity : AppCompatActivity() {

    private val db = FirebaseDatabase.getInstance()
    private val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    private var targetUid = ""
    private var callerUid = ""
    private var callMode = "OUTGOING"
    private var isCallActive = true
    private var isMuted = false
    private var isSpeakerOn = false

    private var rtcClient: WebRTCClient? = null
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call)

        val targetName = intent.getStringExtra("TARGET_NAME") ?: "Unknown Agent"
        targetUid = intent.getStringExtra("TARGET_UID") ?: ""
        callerUid = intent.getStringExtra("CALLER_UID") ?: ""
        callMode = intent.getStringExtra("CALL_MODE") ?: "OUTGOING"

        findViewById<TextView>(R.id.tvCallerName).text = targetName
        
        val tvCallerInitial = findViewById<TextView>(R.id.tvCallerInitial)
        tvCallerInitial.text = targetName.take(1).uppercase()
        
        val btnAccept = findViewById<ImageButton>(R.id.btnAcceptCall)
        val btnEnd = findViewById<ImageButton>(R.id.btnEndCall)
        tvStatus = findViewById(R.id.tvCallStatus)

        val btnMute = findViewById<ImageButton>(R.id.btnMute)
        val btnSpeaker = findViewById<ImageButton>(R.id.btnSpeaker)

        // Initialize WebRTC
        rtcClient = WebRTCClient(this, myUid, if (callMode == "OUTGOING") targetUid else callerUid, object : WebRTCClient.WebRTCListener {
            override fun onCallReady() {
                runOnUiThread { tvStatus.text = "SECURE CHANNEL ESTABLISHED" }
            }
            override fun onCallEnded() {
                runOnUiThread { 
                    tvStatus.text = "Connection Terminated"
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ finish() }, 1000)
                }
            }
        })

        // Permissions Check
        if (androidx.core.app.ActivityCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            androidx.core.app.ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.RECORD_AUDIO), 101)
        }

        // Set Audio Mode for Voice Call
        val audioManager = getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
        audioManager.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
        audioManager.isMicrophoneMute = false

        if (callMode == "OUTGOING") {
            tvStatus.text = "Initiating Secure Uplink..."
            btnAccept.visibility = View.GONE
            
            var hasStarted = false
            val myCallRef = db.getReference("calls").child(targetUid)
            myCallRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!isCallActive) return
                    val status = snapshot.child("status").getValue(String::class.java)
                    if (status == "accepted" && !hasStarted) {
                        hasStarted = true
                        tvStatus.text = "Syncing Node..."
                        rtcClient?.startCall()
                    } else if (status == "rejected" || !snapshot.exists()) {
                        tvStatus.text = "Call Rejected"
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ finish() }, 1000)
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
            
            btnEnd.setOnClickListener {
                terminateCall(targetUid)
            }
        } else {
            // INCOMING
            tvStatus.text = "Incoming Secure Signal..."
            btnAccept.visibility = View.VISIBLE
            
            val myCallRef = db.getReference("calls").child(myUid)
            myCallRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!isCallActive) return
                    if (!snapshot.exists()) finish()
                }
                override fun onCancelled(error: DatabaseError) {}
            })
            
            btnAccept.setOnClickListener {
                myCallRef.child("status").setValue("accepted")
                tvStatus.text = "Connecting..."
                btnAccept.visibility = View.GONE
                rtcClient?.acceptCall()
            }
            
            btnEnd.setOnClickListener {
                terminateCall(myUid)
            }
        }

        // Mute Toggle
        btnMute.setOnClickListener {
            isMuted = !isMuted
            rtcClient?.toggleMute(isMuted)
            if (isMuted) {
                btnMute.setColorFilter(android.graphics.Color.RED)
            } else {
                btnMute.clearColorFilter()
            }
            Toast.makeText(this, if (isMuted) "Mic Muted" else "Mic Active", Toast.LENGTH_SHORT).show()
        }

        // Speaker Toggle (Modern API)
        btnSpeaker.setOnClickListener {
            isSpeakerOn = !isSpeakerOn
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val devices = audioManager.availableCommunicationDevices
                val speakerDevice = devices.find { it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                if (isSpeakerOn && speakerDevice != null) {
                    audioManager.setCommunicationDevice(speakerDevice)
                } else {
                    audioManager.clearCommunicationDevice()
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = isSpeakerOn
            }
            if (isSpeakerOn) {
                btnSpeaker.setColorFilter(android.graphics.Color.GREEN)
            } else {
                btnSpeaker.clearColorFilter()
            }
            Toast.makeText(this, if (isSpeakerOn) "Speaker ON" else "Handset Mode", Toast.LENGTH_SHORT).show()
        }
    }

    private fun terminateCall(nodeUid: String) {
        val audioManager = getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
        audioManager.mode = android.media.AudioManager.MODE_NORMAL
        isCallActive = false
        db.getReference("calls").child(nodeUid).removeValue()
        rtcClient?.close()
        finish()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        rtcClient?.close()
    }
}
