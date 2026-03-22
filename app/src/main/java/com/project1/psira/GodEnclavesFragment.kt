package com.project1.psira

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*

class GodEnclavesFragment : Fragment() {
    private lateinit var groupList: ArrayList<Group>
    private lateinit var adapter: GodGroupAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_god_list, container, false)
        val rv: RecyclerView = view.findViewById(R.id.rvGodList)
        rv.layoutManager = LinearLayoutManager(context)
        groupList = ArrayList()
        adapter = GodGroupAdapter(groupList)
        rv.adapter = adapter

        FirebaseDatabase.getInstance().getReference("groups").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                groupList.clear()
                for (child in snapshot.children) {
                    val group = child.getValue(Group::class.java)
                    if (group != null) groupList.add(group)
                }
                adapter.notifyDataSetChanged()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
        return view
    }
}
