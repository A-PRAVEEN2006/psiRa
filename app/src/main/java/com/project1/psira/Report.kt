package com.project1.psira

data class Report(
    var id: String? = null,
    val reportedUserId: String? = null,
    val reason: String? = null,
    val timestamp: Long? = null,
    val lastMessages: List<String>? = null
)
