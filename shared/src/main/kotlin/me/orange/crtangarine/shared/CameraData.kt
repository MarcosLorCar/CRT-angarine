package me.orange.crtangarine.shared

import kotlinx.serialization.Serializable

@Serializable
data class CameraData(
    val id: String,
    val name: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val isOnline: Boolean
)
