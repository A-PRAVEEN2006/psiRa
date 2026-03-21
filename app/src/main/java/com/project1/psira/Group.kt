package com.project1.psira

data class Group(
    var id: String? = null,
    val name: String? = null,
    val createdBy: String? = null,
    val adminUids: Map<String, Boolean> = emptyMap(),
    val imageBase64: String? = null,
    val memberCount: Int = 1
) {
    constructor() : this(null, null, null, emptyMap(), null, 1)
}
