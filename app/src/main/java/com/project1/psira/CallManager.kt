package com.project1.psira

import android.content.Context
import android.content.Intent
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

object CallManager {
    private var isListening = false
    private var currentContext: Context? = null
    private var lastCallCallerUid: String? = null

    fun listenForIncomingCalls(context: Context) {
        currentContext = context
        if (isListening) return
        
        val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val ref = FirebaseDatabase.getInstance().getReference("calls").child(myUid)
        
        ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    lastCallCallerUid = null // Reset when call terminates
                    return
                }
                
                val status = snapshot.child("status").getValue(String::class.java)
                val callerUid = snapshot.child("callerUid").getValue(String::class.java)
                val callerName = snapshot.child("callerName").getValue(String::class.java)
                
                if (status == "calling" && callerUid != null && callerName != null && callerUid != lastCallCallerUid) {
                    lastCallCallerUid = callerUid
                    val intent = Intent(currentContext, CallActivity::class.java).apply {
                        putExtra("TARGET_NAME", callerName)
                        putExtra("CALLER_UID", callerUid)
                        putExtra("CALL_MODE", "INCOMING")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    currentContext?.startActivity(intent)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
        isListening = true
    }
    
    fun updateContext(context: Context) {
        currentContext = context
    }
}
