package com.juni.app.data.vault

data class VaultEntry(
    val relativePath: String,
    val name: String,
    val isDirectory: Boolean,
    val lastModified: Long,
    val sizeBytes: Long,
)

data class VaultHit(
    val relativePath: String,
    val snippet: String,
)
