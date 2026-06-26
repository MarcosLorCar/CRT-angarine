package me.orange.crtangarine.shared

import kotlinx.serialization.Serializable

@Serializable
data class CameraData(
    val id: String,
    val name: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val isOnline: Boolean,
    val stationName: String = ""
)

@Serializable
data class CameraInfo(
    val pos: String,
    val name: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val isOnline: Boolean
)

@Serializable
data class StationInfo(
    val ownerUuid: String,
    val customName: String,
    val pos: String,
    val cameras: List<CameraInfo>
)

@Serializable
data class CameraRegistryUpdate(
    val stations: List<StationInfo>
)
