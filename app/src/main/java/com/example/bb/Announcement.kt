package com.example.bb

enum class MessageType { TEXT_ONLY, FILE_UPLOAD, ASSIGNMENT }

data class Announcement(
    val id: String,
    val title: String,
    val body: String,
    val senderName: String,
    val date: String,
    val type: MessageType,
    val attachmentName: String? = null,
    val attachmentUrl: String? = null
)
