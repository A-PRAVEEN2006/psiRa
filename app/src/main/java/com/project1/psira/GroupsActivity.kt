package com.project1.psira

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.WindowManager
import android.widget.EditText
import android.widget.Toast
import java.io.ByteArrayOutputStream
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts

class GroupsActivity : AppCompatActivity() {

    private lateinit var groupAdapter: GroupAdapter
    private lateinit var groupList: ArrayList<Group>
    private lateinit var db: FirebaseDatabase

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val uri = result.data?.data
            if (uri != null && selectedGroupIdForImage != null) {
                try {
                    val inputStream = contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    
                    val outputStream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 40, outputStream)
                    val byteArray = outputStream.toByteArray()
                    val base64Image = Base64.encodeToString(byteArray, Base64.DEFAULT)
                    
                    db.getReference("groups").child(selectedGroupIdForImage!!).child("imageBase64").setValue(base64Image)
                    Toast.makeText(this, "Group Icon Updated!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Failed to encode image.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_groups)

        db = FirebaseDatabase.getInstance()
        val user = FirebaseAuth.getInstance().currentUser

        val rvGroups: RecyclerView = findViewById(R.id.rvGroups)
        rvGroups.layoutManager = LinearLayoutManager(this)
        groupList = ArrayList()
        groupAdapter = GroupAdapter(groupList) { clickedGroup ->
            handleGroupLongClick(clickedGroup)
        }
        rvGroups.adapter = groupAdapter

        // Load Groups the user has joined
        if (user != null) {
            val userGroupsRef = db.getReference("user_groups").child(user.uid)
            userGroupsRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    groupList.clear()
                    for (groupSnapshot in snapshot.children) {
                        val groupId = groupSnapshot.key
                        if (groupId != null) {
                            db.getReference("groups").child(groupId).get().addOnSuccessListener { details ->
                                val group = details.getValue(Group::class.java)
                                if (group != null) {
                                    groupList.add(group)
                                    groupAdapter.notifyDataSetChanged()
                                }
                            }
                        }
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        }

        // Floating Action Button to Create Groups
        val fabAddGroup: FloatingActionButton = findViewById(R.id.fabAddGroup)
        fabAddGroup.setOnClickListener {
            showCreateGroupDialog()
        }

        // Bottom Nav Listeners
        val bottomNav: BottomNavigationView = findViewById(R.id.bottomNavGroups)
        bottomNav.selectedItemId = R.id.nav_groups
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, NexusDashboardActivity::class.java))
                    finish()
                    false
                }
                R.id.nav_chats -> {
                    startActivity(Intent(this@GroupsActivity, ChatsActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_groups -> {
                    true
                }
                R.id.nav_death_note -> {
                    VaultAuthHelper.authenticateAndLaunch(this@GroupsActivity, bottomNav, R.id.nav_groups)
                    false
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    finish()
                    false
                }
                else -> false
            }
        }
    }

    private fun showCreateGroupDialog() {
        val input = EditText(this)
        input.hint = "Enter Dark Group Name"
        
        AlertDialog.Builder(this)
            .setTitle("Create Enclave")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    createGroup(name)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createGroup(name: String) {
        val groupId = (10000..99999).random().toString()
        val user = FirebaseAuth.getInstance().currentUser ?: return
        
        val newGroup = Group(
            id = groupId,
            name = name,
            createdBy = user.uid,
            adminUids = mapOf(user.uid to true),
            imageBase64 = null,
            memberCount = 1
        )
        
        db.getReference("groups").child(groupId).setValue(newGroup).addOnSuccessListener {
            db.getReference("user_groups").child(user.uid).child(groupId).setValue(true)
            Toast.makeText(this, "Enclave Created: #$groupId", Toast.LENGTH_LONG).show()
        }
    }

    private var selectedGroupIdForImage: String? = null

    private fun handleGroupLongClick(group: Group) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null && group.adminUids.containsKey(user.uid)) {
            val options = arrayOf("Add Member", "Change Group Icon", "Destroy Enclave")
            AlertDialog.Builder(this)
                .setTitle("Admin Control: ${group.name}")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> showAddMemberDialog(group)
                        1 -> pickGroupImage(group.id!!)
                        2 -> confirmDestroyGroup(group)
                    }
                }.show()
        } else {
            Toast.makeText(this, "Admin privileges required.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAddMemberDialog(group: Group) {
        if (group.memberCount >= 50) {
            Toast.makeText(this, "Group is full (50/50)", Toast.LENGTH_SHORT).show()
            return
        }
        val input = EditText(this)
        input.hint = "Enter 5-Digit Agent ID"
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        AlertDialog.Builder(this)
            .setTitle("Add Member")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val agentId = input.text.toString().trim()
                if (agentId.length == 5) {
                    addMemberByAgentId(group, agentId)
                } else {
                    Toast.makeText(this, "ID must be 5 digits", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addMemberByAgentId(group: Group, agentId: String) {
        val usersRef = FirebaseDatabase.getInstance().getReference("users")
        usersRef.orderByChild("agentId").equalTo(agentId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val uid = snapshot.children.first().key
                    if (uid != null) {
                        db.getReference("user_groups").child(uid).child(group.id!!).setValue(true)
                        db.getReference("groups").child(group.id!!).child("memberCount").setValue(group.memberCount + 1)
                        Toast.makeText(this@GroupsActivity, "Agent added securely.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@GroupsActivity, "Agent ID not found in database.", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun confirmDestroyGroup(group: Group) {
        AlertDialog.Builder(this)
            .setTitle("WIPE ENCLAVE?")
            .setMessage("This will permanently shred the group and all its encrypted messages.")
            .setPositiveButton("WIPE") { _, _ ->
                db.getReference("groups").child(group.id!!).removeValue()
                db.getReference("group_${group.id}").removeValue() // Delete chat history
                Toast.makeText(this, "Enclave completely eradicated.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pickGroupImage(groupId: String) {
        selectedGroupIdForImage = groupId
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        imagePickerLauncher.launch(intent)
    }


}
