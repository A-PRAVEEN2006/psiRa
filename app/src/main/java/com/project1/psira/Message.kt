package com.project1.psira

data class Message(
    var id: String? = null,
    val sender: String? = null,
    val content: String? = null,
    val isBurnable: Boolean = false,
    val type: String = "text", // text, voice, image, doc, steg
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "sent", // sent, delivered, read
    val replyToId: String? = null,
    val replyToSender: String? = null,
    val replyToText: String? = null
) {
    // Empty constructor for Firebase reflection
    constructor() : this(null, null, null, false, "text", System.currentTimeMillis(), "sent", null, null, null)
}
