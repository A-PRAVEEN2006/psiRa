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
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class ChatsActivity : AppCompatActivity() {

    private lateinit var chatList: ArrayList<User>
    private lateinit var chatAdapter: DirectChatAdapter

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
            val listenersMap = mutableMapOf<String, ValueEventListener>()
            db.getReference("user_direct_chats").child(currentUser.uid)
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val newUids = snapshot.children.mapNotNull { it.key }
                        
                        // Remove items that are no longer in the list
                        val toRemove = listenersMap.keys.filter { it !in newUids }
                        for (uid in toRemove) {
                            val listener = listenersMap.remove(uid)
                            if (listener != null) {
                                db.getReference("users").child(uid).removeEventListener(listener)
                            }
                            chatList.removeAll { it.uid == uid }
                        }
                        chatAdapter.notifyDataSetChanged()
                        
                        for (targetUid in newUids) {
                            if (!listenersMap.containsKey(targetUid)) {
                                val listener = object : ValueEventListener {
                                    override fun onDataChange(userSnap: DataSnapshot) {
                                        val user = userSnap.getValue(User::class.java)
                                        if (user != null) {
                                            val fullUser = User(uid = targetUid, email = user.email, name = user.name, agentId = user.agentId, banned = user.banned, isOnline = user.isOnline)
                                            val existingIndex = chatList.indexOfFirst { it.uid == targetUid }
                                            if (existingIndex >= 0) {
                                                chatList[existingIndex] = fullUser
                                            } else {
                                                chatList.add(fullUser)
                                            }
                                            chatAdapter.notifyDataSetChanged()
                                        }
                                    }
                                    override fun onCancelled(error: DatabaseError) {}
                                }
                                listenersMap[targetUid] = listener
                                db.getReference("users").child(targetUid).addValueEventListener(listener)
                            }
                        }
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })
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
    }

    override fun onResume() {
        super.onResume()
        CallManager.listenForIncomingCalls(this)
        CallManager.updateContext(this)
    }

    private fun findAgentAndChat(agentId: String) {
        val db = FirebaseDatabase.getInstance().getReference("users")
        db.orderByChild("agentId").equalTo(agentId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
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
                } else {
                    Toast.makeText(this@ChatsActivity, "Agent ID not found in the matrix.", Toast.LENGTH_LONG).show()
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }
}
