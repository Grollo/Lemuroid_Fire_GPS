package com.swordfish.lemuroid.app.shared.firegps

data class GpsArea(
    val id: Int,
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float
)
