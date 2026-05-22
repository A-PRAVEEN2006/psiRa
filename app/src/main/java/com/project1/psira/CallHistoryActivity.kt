package com.project1.psira

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class CallHistoryActivity : BaseActivity() {

    data class CallLogEntry(
        val targetName: String,
        val targetUid: String,
        val timestamp: Long,
        val durationSeconds: Long,
        val callType: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call_history)

        findViewById<View>(R.id.btnBack).setOnClickListener {
            finish()
        }

        val rvCallHistory = findViewById<RecyclerView>(R.id.rvCallHistory)
        rvCallHistory.layoutManager = LinearLayoutManager(this)

        loadCallLogs(rvCallHistory)
    }

    private fun loadCallLogs(recyclerView: RecyclerView) {
        val prefs = getSharedPreferences("CallLogsPrefs", Context.MODE_PRIVATE)
        val json = prefs.getString("logs", "[]") ?: "[]"
        val logsList = mutableListOf<CallLogEntry>()

        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                logsList.add(
                    CallLogEntry(
                        targetName = o.optString("name", "Unknown Node"),
                        targetUid = o.optString("uid", ""),
                        timestamp = o.optLong("ts", 0L),
                        durationSeconds = o.optLong("duration", 0L),
                        callType = o.optString("type", "OUTGOING")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val layoutEmptyLogs = findViewById<View>(R.id.layoutEmptyLogs)
        if (logsList.isEmpty()) {
            layoutEmptyLogs.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            layoutEmptyLogs.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            recyclerView.adapter = CallHistoryAdapter(logsList)
        }
    }

    private inner class CallHistoryAdapter(private val logs: List<CallLogEntry>) :
        RecyclerView.Adapter<CallHistoryAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivType: ImageView = view.findViewById(R.id.ivType)
            val tvTarget: TextView = view.findViewById(R.id.tvTarget)
            val tvTime: TextView = view.findViewById(R.id.tvTime)
            val tvDuration: TextView = view.findViewById(R.id.tvDuration)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_call_record, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val log = logs[position]
            holder.tvTarget.text = log.targetName

            // Format timestamp
            val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
            holder.tvTime.text = sdf.format(Date(log.timestamp))

            // Format type, icon, and colors
            when (log.callType) {
                "INCOMING" -> {
                    holder.ivType.setImageResource(android.R.drawable.sym_call_incoming)
                    holder.ivType.setColorFilter(android.graphics.Color.parseColor("#4CAF50"))
                    holder.tvDuration.text = formatDuration(log.durationSeconds)
                    holder.tvDuration.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
                }
                "OUTGOING" -> {
                    holder.ivType.setImageResource(android.R.drawable.sym_call_outgoing)
                    holder.ivType.setColorFilter(android.graphics.Color.parseColor("#7B61FF"))
                    holder.tvDuration.text = formatDuration(log.durationSeconds)
                    holder.tvDuration.setTextColor(android.graphics.Color.parseColor("#7B61FF"))
                }
                "MISSED" -> {
                    holder.ivType.setImageResource(android.R.drawable.sym_call_incoming)
                    holder.ivType.setColorFilter(android.graphics.Color.parseColor("#FF5252"))
                    holder.tvDuration.text = "Missed"
                    holder.tvDuration.setTextColor(android.graphics.Color.parseColor("#FF5252"))
                }
                else -> {
                    holder.ivType.setImageResource(android.R.drawable.sym_call_outgoing)
                    holder.ivType.setColorFilter(android.graphics.Color.GRAY)
                    holder.tvDuration.text = formatDuration(log.durationSeconds)
                    holder.tvDuration.setTextColor(android.graphics.Color.GRAY)
                }
            }
        }

        override fun getItemCount(): Int = logs.size

        private fun formatDuration(seconds: Long): String {
            val m = seconds / 60
            val s = seconds % 60
            return String.format(Locale.getDefault(), "%02d:%02d", m, s)
        }
    }

    companion object {
        fun saveCallLog(
            context: Context,
            targetName: String,
            targetUid: String,
            timestamp: Long,
            durationSeconds: Long,
            callType: String
        ) {
            val prefs = context.getSharedPreferences("CallLogsPrefs", Context.MODE_PRIVATE)
            val json = prefs.getString("logs", "[]") ?: "[]"
            try {
                val arr = JSONArray(json)
                val newLog = JSONObject().apply {
                    put("name", targetName)
                    put("uid", targetUid)
                    put("ts", timestamp)
                    put("duration", durationSeconds)
                    put("type", callType)
                }
                val newArr = JSONArray()
                newArr.put(newLog)
                for (i in 0 until arr.length()) {
                    newArr.put(arr.get(i))
                }
                // Cap to 100 entries
                val limit = 100
                val cappedArr = JSONArray()
                for (i in 0 until minOf(newArr.length(), limit)) {
                    cappedArr.put(newArr.get(i))
                }
                prefs.edit().putString("logs", cappedArr.toString()).apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}