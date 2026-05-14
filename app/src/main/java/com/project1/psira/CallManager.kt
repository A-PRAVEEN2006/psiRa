package com.project1.psira

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

object CallManager {
    private var isListening = false
    private var currentContext: Context? = null
    private var lastCallCallerUid: String? = null
    private var activeBottomSheet: BottomSheetDialog? = null

    fun listenForIncomingCalls(context: Context) {
        currentContext = context
        if (isListening) return

        val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val ref = FirebaseDatabase.getInstance().getReference("calls").child(myUid)

        ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    lastCallCallerUid = null
                    // Dismiss any open incoming-call dialog if the caller cancelled
                    activeBottomSheet?.dismiss()
                    activeBottomSheet = null
                    return
                }

                val status = snapshot.child("status").getValue(String::class.java)
                val callerUid = snapshot.child("callerUid").getValue(String::class.java)
                val callerName = snapshot.child("callerName").getValue(String::class.java)

                if (status == "calling" && callerUid != null && callerName != null && callerUid != lastCallCallerUid) {
                    lastCallCallerUid = callerUid
                    showInAppIncomingCall(callerUid, callerName)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
        isListening = true
    }

    fun updateContext(context: Context) {
        currentContext = context
    }

    private fun showInAppIncomingCall(callerUid: String, callerName: String) {
        val ctx = currentContext as? AppCompatActivity ?: return

        ctx.runOnUiThread {
            // Dismiss any existing sheet first
            activeBottomSheet?.dismiss()

            val bottomSheet = BottomSheetDialog(ctx, R.style.IncomingCallBottomSheet)
            activeBottomSheet = bottomSheet

            val view = LayoutInflater.from(ctx).inflate(R.layout.dialog_incoming_call, null)
            view.findViewById<TextView>(R.id.tvIncomingCallerName).text = callerName

            // ACCEPT
            view.findViewById<ImageButton>(R.id.btnAcceptIncoming).setOnClickListener {
                bottomSheet.dismiss()
                activeBottomSheet = null
                // Update call status in Firebase
                FirebaseDatabase.getInstance()
                    .getReference("calls")
                    .child(FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener)
                    .child("status").setValue("accepted")
                // Launch CallActivity
                val intent = Intent(ctx, CallActivity::class.java).apply {
                    putExtra("TARGET_NAME", callerName)
                    putExtra("CALLER_UID", callerUid)
                    putExtra("CALL_MODE", "INCOMING")
                    putExtra("ALREADY_ACCEPTED", true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(intent)
            }

            // DECLINE
            view.findViewById<ImageButton>(R.id.btnDeclineCall).setOnClickListener {
                bottomSheet.dismiss()
                activeBottomSheet = null
                lastCallCallerUid = null
                // Remove call node so caller knows we declined
                FirebaseDatabase.getInstance()
                    .getReference("calls")
                    .child(FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener)
                    .removeValue()
            }

            bottomSheet.setContentView(view)
            bottomSheet.show()
        }
    }
}
