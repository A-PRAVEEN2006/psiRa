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
                    startActivity(Intent(this@ChatsActivity, VaultActivity::class.java))
                    finish()
                    true
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
            db.getReference("user_direct_chats").child(currentUser.uid)
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        chatList.clear()
                        for (child in snapshot.children) {
                            val targetUid = child.key
                            if (targetUid != null) {
                                db.getReference("users").child(targetUid).get().addOnSuccessListener { userSnap ->
                                    val user = userSnap.getValue(User::class.java)
                                    if (user != null) {
                                        val fullUser = User(uid = targetUid, email = user.email, name = user.name, agentId = user.agentId)
                                        chatList.add(fullUser)
                                        chatAdapter.notifyDataSetChanged()
                                    }
                                }
                            }
                        }
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })
        }

        val fabNewChat: FloatingActionButton = findViewById(R.id.fabNewChat)
        fabNewChat.setOnClickListener {
            val input = EditText(this)
            input.hint = "Enter 5-Digit Agent ID"
            input.inputType = android.text.InputType.TYPE_CLASS_NUMBER

            AlertDialog.Builder(this)
                .setTitle("Establish Secure Link")
                .setMessage("Enter the 5-Digit Agent ID you wish to contact.")
                .setView(input)
                .setPositiveButton("CONNECT") { _, _ ->
                    val targetId = input.text.toString().trim()
                    if (targetId.length == 5) {
                        findAgentAndChat(targetId)
                    } else {
                        Toast.makeText(this, "Invalid ID Format. Must be 5 digits.", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("CANCEL", null)
                .show()
        }
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
