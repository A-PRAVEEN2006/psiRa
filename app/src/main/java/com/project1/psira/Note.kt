package com.project1.psira

data class Note(
    var id: String? = null,
    val title: String? = null,
    val content: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    // Empty constructor for Firebase
    constructor() : this(null, null, null, 0L)
}
