package com.project1.psira

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class ChatsActivity : BaseActivity() {

    private lateinit var chatList: ArrayList<User>
    private lateinit var chatAdapter: DirectChatAdapter

    private var userDirectChatsRef: DatabaseReference? = null
    private var chatsListener: ValueEventListener? = null
    private val listenersMap = mutableMapOf<String, ValueEventListener>()
    private val messageListenersMap = mutableMapOf<String, ValueEventListener>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_chats)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigationView)
        bottomNav.selectedItemId = R.id.nav_chats
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, NexusDashboardActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_death_note -> {
                    VaultAuthHelper.authenticateAndLaunch(this@ChatsActivity, bottomNav, R.id.nav_chats)
                    false
                }
                R.id.nav_chats -> true
                R.id.nav_groups -> {
                    startActivity(Intent(this, GroupsActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    finish()
                    true
                }
                else -> false
            }
        }

        val rvChats: RecyclerView = findViewById(R.id.rvChats)
        rvChats.layoutManager = LinearLayoutManager(this)
        chatList = ArrayList()
        chatAdapter = DirectChatAdapter(chatList)
        rvChats.adapter = chatAdapter

        val currentUser = FirebaseAuth.getInstance().currentUser
        val db = FirebaseDatabase.getInstance()

        if (currentUser != null) {
            val myUid = currentUser.uid
            userDirectChatsRef = db.getReference("user_direct_chats").child(myUid)
            chatsListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val newUids = snapshot.children.mapNotNull { it.key }
                    
                    // Remove items that are no longer in the list
                    val toRemove = listenersMap.keys.filter { it !in newUids }
                    for (uid in toRemove) {
                        val listener = listenersMap.remove(uid)
                        if (listener != null) {
                            db.getReference("users").child(uid).removeEventListener(listener)
                        }
                        val msgListener = messageListenersMap.remove(uid)
                        if (msgListener != null) {
                            val channelName = if (myUid < uid) "${myUid}_$uid" else "${uid}_${myUid}"
                            db.getReference("direct_messages").child(channelName).removeEventListener(msgListener)
                        }
                        chatList.removeAll { it.uid == uid }
                    }
                    chatAdapter.notifyDataSetChanged()
                    
                    for (targetUid in newUids) {
                        // User Profile listener
                        if (!listenersMap.containsKey(targetUid)) {
                            val listener = object : ValueEventListener {
                                override fun onDataChange(userSnap: DataSnapshot) {
                                    val user = userSnap.getValue(User::class.java)
                                    if (user != null) {
                                        val fullUser = User(uid = targetUid, email = user.email, name = user.name, agentId = user.agentId, banned = user.banned, online = user.online)
                                        val existingIndex = chatList.indexOfFirst { it.uid == targetUid }
                                        if (existingIndex >= 0) {
                                            chatList[existingIndex] = fullUser
                                        } else {
                                            chatList.add(fullUser)
                                        }
                                        chatList.sortByDescending { chatAdapter.getTimestamp(it.uid) }
                                        chatAdapter.notifyDataSetChanged()
                                    }
                                }
                                override fun onCancelled(error: DatabaseError) {}
                            }
                            listenersMap[targetUid] = listener
                            db.getReference("users").child(targetUid).addValueEventListener(listener)
                        }

                        // WhatsApp-style Channel Message Listener (Unread counts & Last Message preview)
                        if (!messageListenersMap.containsKey(targetUid)) {
                            val channelName = if (myUid < targetUid) "${myUid}_$targetUid" else "${targetUid}_${myUid}"
                            val msgListener = object : ValueEventListener {
                                override fun onDataChange(msgSnapshot: DataSnapshot) {
                                    var unreadCount = 0
                                    var totalReceived = 0
                                    var lastMsg: Message? = null

                                    val myDisplayName = FirebaseAuth.getInstance().currentUser?.displayName

                                    for (child in msgSnapshot.children) {
                                        val msg = child.getValue(Message::class.java) ?: continue
                                        val isFromMe = (msg.sender == myDisplayName)
                                        if (!isFromMe) {
                                            totalReceived++
                                            if (msg.status != "read") {
                                                unreadCount++
                                            }
                                        }
                                        lastMsg = msg
                                    }

                                    if (lastMsg != null) {
                                        val isFromMe = (lastMsg.sender == myDisplayName)
                                        var previewContent = ""
                                        if (lastMsg.type == "text") {
                                            val rawContent = lastMsg.content ?: ""
                                            val decrypted = try {
                                                ECDHKeyManager.decryptFromContact(this@ChatsActivity, targetUid, rawContent)
                                            } catch (e: Exception) {
                                                rawContent
                                            }
                                            val decoded = try {
                                                PsiRaConverter.decode(decrypted)
                                            } catch (e: Exception) {
                                                decrypted
                                            }
                                            previewContent = if (decoded.length > 35) decoded.substring(0, 32) + "..." else decoded
                                        }

                                        val info = ChatPreviewInfo(
                                            lastMessage = previewContent,
                                            lastMessageType = lastMsg.type,
                                            timestamp = if (lastMsg.timestamp > 0) lastMsg.timestamp else 0L,
                                            unreadCount = unreadCount,
                                            totalReceived = totalReceived,
                                            isLastFromMe = isFromMe,
                                            lastStatus = lastMsg.status
                                        )
                                        chatAdapter.updatePreview(targetUid, info)
                                        chatList.sortByDescending { chatAdapter.getTimestamp(it.uid) }
                                        chatAdapter.notifyDataSetChanged()
                                    } else {
                                        val info = ChatPreviewInfo(
                                            lastMessage = "",
                                            lastMessageType = "text",
                                            timestamp = 0L,
                                            unreadCount = 0,
                                            totalReceived = 0,
                                            isLastFromMe = false,
                                            lastStatus = "sent"
                                        )
                                        chatAdapter.updatePreview(targetUid, info)
                                    }
                                }

                                override fun onCancelled(error: DatabaseError) {}
                            }
                            messageListenersMap[targetUid] = msgListener
                            db.getReference("direct_messages").child(channelName).addValueEventListener(msgListener)
                        }
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            }
            userDirectChatsRef?.addValueEventListener(chatsListener as ValueEventListener)
        }

        val fabNewChat: FloatingActionButton = findViewById(R.id.fabNewChat)
        fabNewChat.setOnClickListener {
            val input = EditText(this)
            input.hint = "5-Digit Agent ID"
            input.setTextColor(android.graphics.Color.WHITE)
            input.setHintTextColor(android.graphics.Color.GRAY)
            input.inputType = android.text.InputType.TYPE_CLASS_NUMBER

            PsiRaDialogs.showDeleteSheet(
                this,
                "ESTABLISH SECURE LINK",
                "Enter the unique 5-Digit identifier of the node you wish to contact.",
                "CONNECT",
                input
            ) {
                val targetId = input.text.toString().trim()
                if (targetId.length == 5) {
                    findAgentAndChat(targetId)
                } else {
                    Toast.makeText(this, "Invalid ID format.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        findViewById<android.view.View>(R.id.btnCallHistory).setOnClickListener {
            startActivity(Intent(this, CallHistoryActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        CallManager.listenForIncomingCalls(this)
        CallManager.updateContext(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        val currentUser = FirebaseAuth.getInstance().currentUser
        val db = FirebaseDatabase.getInstance()
        if (currentUser != null) {
            chatsListener?.let { userDirectChatsRef?.removeEventListener(it) }
            for ((uid, listener) in listenersMap) {
                db.getReference("users").child(uid).removeEventListener(listener)
            }
            for ((uid, msgListener) in messageListenersMap) {
                val channelName = if (currentUser.uid < uid) "${currentUser.uid}_$uid" else "${uid}_${currentUser.uid}"
                db.getReference("direct_messages").child(channelName).removeEventListener(msgListener)
            }
        }
        listenersMap.clear()
        messageListenersMap.clear()
    }

    private fun findAgentAndChat(agentId: String) {
        Toast.makeText(this, "Scanning secure frequency...", Toast.LENGTH_SHORT).show()
        val db = FirebaseDatabase.getInstance().getReference("users")
        
        // Try searching as String first
        db.orderByChild("agentId").equalTo(agentId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    handleSnapshot(snapshot)
                } else {
                    // Try searching as Long/Int if String fails (for compatibility)
                    val numericId = agentId.toLongOrNull()
                    if (numericId != null) {
                        db.orderByChild("agentId").equalTo(numericId.toDouble()).addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(numSnap: DataSnapshot) {
                                if (numSnap.exists()) {
                                    handleSnapshot(numSnap)
                                } else {
                                    Toast.makeText(this@ChatsActivity, "Agent ID not found in the matrix.", Toast.LENGTH_LONG).show()
                                }
                            }
                            override fun onCancelled(error: DatabaseError) {
                                Toast.makeText(this@ChatsActivity, "Search Error: ${error.message}", Toast.LENGTH_SHORT).show()
                            }
                        })
                    } else {
                        Toast.makeText(this@ChatsActivity, "Agent ID not found in the matrix.", Toast.LENGTH_LONG).show()
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@ChatsActivity, "Search Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun handleSnapshot(snapshot: DataSnapshot) {
        for (child in snapshot.children) {
            val targetUid = child.key
            val targetName = child.child("name").getValue(String::class.java)
            
            if (targetUid == FirebaseAuth.getInstance().currentUser?.uid) {
                Toast.makeText(this@ChatsActivity, "You cannot chat with yourself.", Toast.LENGTH_SHORT).show()
                return
            }
            
            val intent = Intent(this@ChatsActivity, DirectChatActivity::class.java)
            intent.putExtra("TARGET_UID", targetUid)
            intent.putExtra("TARGET_NAME", targetName)
            startActivity(intent)
            return
        }
    }
}
