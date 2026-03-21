package com.project1.psira

data class Message(
    var id: String? = null,    // Firebase key for deletion
    val sender: String? = null,
    val content: String? = null,
    val isBurnable: Boolean = false
) {
    // Empty constructor for Firebase
    constructor() : this(null, null, null, false)
}