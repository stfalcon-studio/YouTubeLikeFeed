package com.stfalcon.videofeed.models

data class MediaObject(
    val title: String,
    val mediaUrl: String,
    val thumbnail: String,
    val description: String,
    var position: Long? = 1
)