package com.project1.psira

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class GodDashboardActivity : AppCompatActivity() {

    private lateinit var userList: ArrayList<User>
    private lateinit var userAdapter: GodUserAdapter
    
    private lateinit var groupList: ArrayList<Group>
    private lateinit var groupAdapter: GodGroupAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_god_dashboard)

        val rvGodUsers: RecyclerView = findViewById(R.id.rvGodUsers)
        rvGodUsers.layoutManager = LinearLayoutManager(this)
        userList = ArrayList()
        userAdapter = GodUserAdapter(userList)
        rvGodUsers.adapter = userAdapter

        val rvGodGroups: RecyclerView = findViewById(R.id.rvGodGroups)
        rvGodGroups.layoutManager = LinearLayoutManager(this)
        groupList = ArrayList()
        groupAdapter = GodGroupAdapter(groupList)
        rvGodGroups.adapter = groupAdapter

        val db = FirebaseDatabase.getInstance()

        // 1. Hook All Users Locally
        db.getReference("users").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                userList.clear()
                for (child in snapshot.children) {
                    val user = child.getValue(User::class.java)
                    // If the user's uid acts as the key but isn't stored, we attach it manually:
                    if (user != null) {
                        val fullUser = User(
                            uid = child.key,
                            email = user.email,
                            name = user.name,
                            agentId = user.agentId,
                            banned = user.banned
                        )
                        userList.add(fullUser)
                    }
                }
                userAdapter.notifyDataSetChanged()
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // 2. Hook All Groups Globally
        db.getReference("groups").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                groupList.clear()
                for (child in snapshot.children) {
                    val group = child.getValue(Group::class.java)
                    if (group != null) {
                        groupList.add(group)
                    }
                }
                groupAdapter.notifyDataSetChanged()
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // 3. Global Broadcast Action
        val btnGlobalBroadcast: Button = findViewById(R.id.btnGlobalBroadcast)
        btnGlobalBroadcast.setOnClickListener {
            val input = EditText(this)
            input.hint = "Enter System Message"

            AlertDialog.Builder(this)
                .setTitle("VOICE OF GOD")
                .setMessage("Submit an override message to completely broadcast across every enclave channel simultaneously.")
                .setView(input)
                .setPositiveButton("SEND") { _, _ ->
                    val text = input.text.toString().trim()
                    if (text.isNotEmpty()) {
                        sendGlobalBroadcast(text)
                    }
                }
                .setNegativeButton("ABORT", null)
                .show()
        }

        // 4. Restore Identity
        val btnRestoreIdentity: Button = findViewById(R.id.btnRestoreIdentity)
        btnRestoreIdentity.setOnClickListener {
            getSharedPreferences("PsiRaPrefs", android.content.Context.MODE_PRIVATE).edit()
                .remove("IMPERSONATING_NAME")
                .remove("IMPERSONATING_ID")
                .apply()
            Toast.makeText(this, "Original creator identity restored.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendGlobalBroadcast(text: String) {
        val db = FirebaseDatabase.getInstance()
        val godMessage = Message(
            id = "GOD_MODE_OVERRIDE",
            sender = "OVERSEER",
            content = "SYSTEM BROADCAST: $text",
            isBurnable = false
        )
        
        var count = 0
        for (group in groupList) {
            val channelName = "group_${group.id}"
            db.getReference("messages").child(channelName).push().setValue(godMessage)
            count++
        }
        Toast.makeText(this, "Injected into $count secured enclaves.", Toast.LENGTH_LONG).show()
    }
}
