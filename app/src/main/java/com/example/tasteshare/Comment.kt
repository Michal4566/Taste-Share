package com.example.tasteshare

import com.google.firebase.Timestamp

data class Comment(
    var userId: String = "",
    var username: String = "",
    var text: String = "",
    var imageUrl: String? = null,
    var timestamp: Timestamp? = null
)
