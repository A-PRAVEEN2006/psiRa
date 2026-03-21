package com.project1.psira

data class User(
    val uid: String? = null,
    val email: String? = null,
    val name: String? = null,
    val agentId: String? = null,
    val banned: Boolean = false
) {
    constructor() : this(null, null, null, null, false)
}
