package com.project1.psira

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class CallHistoryActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call_history)

        val rvCallHistory = findViewById<RecyclerView>(R.id.rvCallHistory)
        rvCallHistory.layoutManager = LinearLayoutManager(this)

        loadCallLogs()
    }

    private fun loadCallLogs() {
        // Placeholder for loading call logs
    }
}