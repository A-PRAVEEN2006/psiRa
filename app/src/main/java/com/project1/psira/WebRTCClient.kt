package com.project1.psira

import android.content.Context
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.ArrayList

class WebRTCClient(
    private val context: Context,
    private val myUid: String,
    private val targetUid: String,
    private val listener: WebRTCListener
) {
    interface WebRTCListener {
        fun onCallReady()
        fun onCallEnded()
    }

    private val db = FirebaseDatabase.getInstance()
    private val signalingRef = db.getReference("calls").child(targetUid)
    private val mySignalingRef = db.getReference("calls").child(myUid)

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioTrack: AudioTrack? = null
    private var audioSource: AudioSource? = null

    private var isLocalDescriptionSet = false
    private var isRemoteDescriptionSet = false
    private val iceCandidateQueue = ArrayList<IceCandidate>()

    private fun showToast(msg: String) {
        (context as? android.app.Activity)?.runOnUiThread {
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    init {
        initPeerConnectionFactory(context)
        peerConnectionFactory = createPeerConnectionFactory()
        peerConnection = createPeerConnection()
    }

    private fun initPeerConnectionFactory(context: Context) {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
    }

    private fun createPeerConnectionFactory(): PeerConnectionFactory {
        val audioDeviceModule = JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()
            
        audioDeviceModule.setMicrophoneMute(false)
        audioDeviceModule.setSpeakerMute(false)

        return PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioDeviceModule)
            .setOptions(PeerConnectionFactory.Options())
            .createPeerConnectionFactory()
    }

    private fun createPeerConnection(): PeerConnection? {
        val iceServers = ArrayList<PeerConnection.IceServer>()
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN

        return peerConnectionFactory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                val candidateMap = mapOf(
                    "sdpMid" to candidate.sdpMid,
                    "sdpMLineIndex" to candidate.sdpMLineIndex,
                    "sdp" to candidate.sdp
                )
                mySignalingRef.child("iceCandidates").push().setValue(candidateMap)
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                if (state == PeerConnection.IceConnectionState.CONNECTED) {
                    listener.onCallReady()
                } else if (state == PeerConnection.IceConnectionState.DISCONNECTED || state == PeerConnection.IceConnectionState.CLOSED) {
                    listener.onCallEnded()
                }
            }
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(p0: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                val track = receiver?.track()
                if (track is AudioTrack) {
                    track.setEnabled(true)
                    track.setVolume(1.0)
                    (context as? android.app.Activity)?.runOnUiThread {
                        android.widget.Toast.makeText(context, "Secure Voice Stream Active", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    fun startCall() {
        audioSource = peerConnectionFactory?.createAudioSource(MediaConstraints())
        localAudioTrack = peerConnectionFactory?.createAudioTrack("101", audioSource)
        peerConnection?.addTrack(localAudioTrack)

        val mediaConstraints = MediaConstraints()
        mediaConstraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() {
                        isLocalDescriptionSet = true
                        signalingRef.child("offer").setValue(sdp.description)
                    }
                    override fun onCreateFailure(p0: String?) { showToast("Offer Set Failed: $p0") }
                    override fun onSetFailure(p0: String?) { showToast("Offer Set Failure: $p0") }
                }, sdp)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) { showToast("Offer Create Failed: $p0") }
            override fun onSetFailure(p0: String?) {}
        }, mediaConstraints)

        listenForAnswer()
        listenForIceCandidates(targetUid)
    }

    fun acceptCall() {
        audioSource = peerConnectionFactory?.createAudioSource(MediaConstraints())
        localAudioTrack = peerConnectionFactory?.createAudioTrack("101", audioSource)
        peerConnection?.addTrack(localAudioTrack)

        listenForOffer()
        listenForIceCandidates(targetUid)
    }

    private fun listenForOffer() {
        offerListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (isRemoteDescriptionSet) return
                val sdpDescription = snapshot.getValue(String::class.java) ?: return
                val sdp = SessionDescription(SessionDescription.Type.OFFER, sdpDescription)
                peerConnection?.setRemoteDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() {
                        isRemoteDescriptionSet = true
                        drainIceCandidates()
                        createAnswer()
                    }
                    override fun onCreateFailure(p0: String?) { showToast("Offer Processing Failed: $p0") }
                    override fun onSetFailure(p0: String?) { showToast("Offer Processing Failure: $p0") }
                }, sdp)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        mySignalingRef.child("offer").addValueEventListener(offerListener!!)
    }

    private fun createAnswer() {
        val mediaConstraints = MediaConstraints()
        mediaConstraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))

        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() {
                        isLocalDescriptionSet = true
                        mySignalingRef.child("answer").setValue(sdp.description)
                    }
                    override fun onCreateFailure(p0: String?) { showToast("Answer set local failed: $p0") }
                    override fun onSetFailure(p0: String?) { showToast("Answer set local failure: $p0") }
                }, sdp)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) { showToast("Answer create failed: $p0") }
            override fun onSetFailure(p0: String?) {}
        }, mediaConstraints)
    }

    private fun listenForAnswer() {
        answerListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isLocalDescriptionSet) return // Signaling Guard: Wait for local offer to be set
                if (isRemoteDescriptionSet) return // Guard: Only set answer once
                val sdpDescription = snapshot.getValue(String::class.java) ?: return
                val sdp = SessionDescription(SessionDescription.Type.ANSWER, sdpDescription)
                peerConnection?.setRemoteDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() {
                        isRemoteDescriptionSet = true
                        drainIceCandidates()
                    }
                    override fun onCreateFailure(p0: String?) { showToast("Answer Set Failed: $p0") }
                    override fun onSetFailure(p0: String?) { showToast("Answer Set Failure: $p0") }
                }, sdp)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        signalingRef.child("answer").addValueEventListener(answerListener!!)
    }

    private fun listenForIceCandidates(remoteUid: String) {
        iceListener = object : com.google.firebase.database.ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val sdpMid = snapshot.child("sdpMid").getValue(String::class.java) ?: return
                val sdpMLineIndex = snapshot.child("sdpMLineIndex").getValue(Int::class.java) ?: return
                val sdp = snapshot.child("sdp").getValue(String::class.java) ?: return
                
                val candidate = IceCandidate(sdpMid, sdpMLineIndex, sdp)
                if (isRemoteDescriptionSet) {
                    peerConnection?.addIceCandidate(candidate)
                } else {
                    iceCandidateQueue.add(candidate)
                }
            }
            override fun onChildChanged(p0: DataSnapshot, p1: String?) {}
            override fun onChildRemoved(p0: DataSnapshot) {}
            override fun onChildMoved(p0: DataSnapshot, p1: String?) {}
            override fun onCancelled(p0: DatabaseError) { showToast("ICE Handshake Interrupted") }
        }
        db.getReference("calls").child(remoteUid).child("iceCandidates").addChildEventListener(iceListener!!)
    }


    private fun drainIceCandidates() {
        for (candidate in iceCandidateQueue) {
            peerConnection?.addIceCandidate(candidate)
        }
        iceCandidateQueue.clear()
    }

    fun toggleMute(isMuted: Boolean) {
        localAudioTrack?.setEnabled(!isMuted)
    }

    private var offerListener: ValueEventListener? = null
    private var answerListener: ValueEventListener? = null
    private var iceListener: com.google.firebase.database.ChildEventListener? = null

    fun close() {
        offerListener?.let { mySignalingRef.child("offer").removeEventListener(it) }
        answerListener?.let { signalingRef.child("answer").removeEventListener(it) }
        iceListener?.let { db.getReference("calls").child(targetUid).child("iceCandidates").removeEventListener(it) }
        
        peerConnection?.close()
        peerConnectionFactory?.dispose()
        audioSource?.dispose()
        
        isLocalDescriptionSet = false
        isRemoteDescriptionSet = false
        iceCandidateQueue.clear()
    }

}
