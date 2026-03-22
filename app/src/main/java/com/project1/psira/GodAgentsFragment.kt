package com.project1.psira

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*

class GodAgentsFragment : Fragment() {
    private lateinit var userList: ArrayList<User>
    private lateinit var adapter: GodUserAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_god_list, container, false)
        val rv: RecyclerView = view.findViewById(R.id.rvGodList)
        rv.layoutManager = LinearLayoutManager(context)
        userList = ArrayList()
        adapter = GodUserAdapter(userList)
        rv.adapter = adapter

        FirebaseDatabase.getInstance().getReference("users").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                userList.clear()
                for (child in snapshot.children) {
                    val user = child.getValue(User::class.java)
                    if (user != null) {
                        userList.add(User(child.key, user.email, user.name, user.agentId, user.banned))
                    }
                }
                adapter.notifyDataSetChanged()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
        return view
    }
}
