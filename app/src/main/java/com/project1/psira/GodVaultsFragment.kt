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

class GodVaultsFragment : Fragment() {
    private lateinit var vaultList: ArrayList<VaultIntercept>
    private lateinit var adapter: GodVaultAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_god_list, container, false)
        val rv: RecyclerView = view.findViewById(R.id.rvGodList)
        rv.layoutManager = LinearLayoutManager(context)
        vaultList = ArrayList()
        adapter = GodVaultAdapter(vaultList)
        rv.adapter = adapter

        FirebaseDatabase.getInstance().getReference("user_vault").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                vaultList.clear()
                for (userVault in snapshot.children) {
                    val uid = userVault.key ?: continue
                    val notes = userVault.child("notes")
                    for (noteSnap in notes.children) {
                        val note = noteSnap.getValue(Note::class.java)
                        if (note != null) {
                            try {
                                val dTitle = PsiRaConverter.decode(AESEncryption.decrypt(note.title ?: ""))
                                val dContent = PsiRaConverter.decode(AESEncryption.decrypt(note.content ?: ""))
                                vaultList.add(VaultIntercept(uid, dTitle, dContent))
                            } catch (e: Exception) {
                                vaultList.add(VaultIntercept(uid, "LOCKED", "[PEEK FAILED]"))
                            }
                        }
                    }
                }
                adapter.notifyDataSetChanged()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
        return view
    }

    data class VaultIntercept(val uid: String, val title: String, val content: String)

    class GodVaultAdapter(private val list: List<VaultIntercept>) : RecyclerView.Adapter<GodVaultAdapter.ViewHolder>() {
        class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tvOwner: TextView = v.findViewById(R.id.tvGodVaultOwner)
            val tvTitle: TextView = v.findViewById(R.id.tvGodVaultNoteTitle)
            val tvMsg: TextView = v.findViewById(R.id.tvGodVaultPreview)
        }
        override fun onCreateViewHolder(p: ViewGroup, t: Int) = ViewHolder(LayoutInflater.from(p.context).inflate(R.layout.item_god_vault_note, p, false))
        override fun onBindViewHolder(h: ViewHolder, p: Int) {
            val item = list[p]
            h.tvOwner.text = "VAULT: ${item.uid.take(8)}..."
            h.tvTitle.text = item.title
            h.tvMsg.text = item.content
        }
        override fun getItemCount() = list.size
    }
}
