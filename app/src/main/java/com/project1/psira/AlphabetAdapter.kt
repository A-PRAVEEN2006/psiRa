package com.project1.psira

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AlphabetAdapter(private val items: List<Pair<String, String>>) :
    RecyclerView.Adapter<AlphabetAdapter.AlphabetViewHolder>() {

    class AlphabetViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvSymbol: TextView = view.findViewById(R.id.tvCipherSymbol)
        val tvEnglish: TextView = view.findViewById(R.id.tvEnglishLetter)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlphabetViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_alphabet_card, parent, false)
        return AlphabetViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlphabetViewHolder, position: Int) {
        val (symbol, english) = items[position]
        holder.tvSymbol.text = symbol
        holder.tvEnglish.text = english
    }

    override fun getItemCount() = items.size
}
