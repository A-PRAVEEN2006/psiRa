package com.project1.psira

data class Message(
    var id: String? = null,
    val sender: String? = null,
    val content: String? = null,
    val isBurnable: Boolean = false,
    val type: String = "text" // New field: text, voice, image, doc
) {
    // Empty constructor for Firebase
    constructor() : this(null, null, null, false, "text")
}