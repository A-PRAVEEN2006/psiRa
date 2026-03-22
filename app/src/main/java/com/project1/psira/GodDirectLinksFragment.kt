package com.project1.psira

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*

class GodDirectLinksFragment : Fragment() {
    private lateinit var linkList: ArrayList<DirectLinkIntercept>
    private lateinit var adapter: GodDirectLinkAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_god_list, container, false)
        val rv: RecyclerView = view.findViewById(R.id.rvGodList)
        rv.layoutManager = LinearLayoutManager(context)
        linkList = ArrayList()
        adapter = GodDirectLinkAdapter(linkList)
        rv.adapter = adapter

        FirebaseDatabase.getInstance().getReference("direct_messages").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                linkList.clear()
                for (child in snapshot.children) {
                    val channelId = child.key ?: continue
                    val lastSnapshot = child.children.lastOrNull()
                    val lastMsg = lastSnapshot?.getValue(Message::class.java)
                    
                    if (lastMsg != null) {
                        try {
                            val decrypted = AESEncryption.decrypt(lastMsg.content ?: "")
                            linkList.add(DirectLinkIntercept(channelId, decrypted))
                        } catch (e: Exception) {
                            linkList.add(DirectLinkIntercept(channelId, "[ENCRYPTED NODE]"))
                        }
                    }
                }
                adapter.notifyDataSetChanged()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
        return view
    }

    data class DirectLinkIntercept(val channelId: String, val lastMessage: String)

    class GodDirectLinkAdapter(private val list: List<DirectLinkIntercept>) : RecyclerView.Adapter<GodDirectLinkAdapter.ViewHolder>() {
        class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tvHeader: TextView = v.findViewById(R.id.tvGodLinkHeader)
            val tvMsg: TextView = v.findViewById(R.id.tvGodLinkLastMessage)
        }
        override fun onCreateViewHolder(p: ViewGroup, t: Int) = ViewHolder(LayoutInflater.from(p.context).inflate(R.layout.item_god_direct_link, p, false))
        override fun onBindViewHolder(h: ViewHolder, p: Int) {
            val item = list[p]
            h.tvHeader.text = "LINK: ${item.channelId}"
            h.tvMsg.text = item.lastMessage
        }
        override fun getItemCount() = list.size
    }
}
