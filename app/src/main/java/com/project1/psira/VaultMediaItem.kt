package com.project1.psira

data class VaultMediaItem(
    val id: String = "",
    val originalName: String = "",
    val mimeType: String = "",
    val sizeBytes: Long = 0L,
    val importedAt: Long = 0L,
    val encFileName: String = "",
    val thumbFileName: String = ""
) {
    fun isVideo() = mimeType.startsWith("video/")
    fun isImage() = mimeType.startsWith("image/")
}
