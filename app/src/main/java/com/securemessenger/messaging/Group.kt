package com.securemessenger.messaging

import java.util.UUID

data class Group(
    val id: UUID,
    val memberOnions: List<String>,
    val createdAt: Long = System.currentTimeMillis(),
)
