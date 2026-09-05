package com.enesuzun2002.wanotify.domain.model

data class ProcessedNotification(
    val title: String,
    val text: String,
    val timestamp: Long,
    val stableId: Int = (title + text + timestamp).hashCode()
)