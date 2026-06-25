package me.orange.crtangarine.shared

import kotlinx.serialization.Serializable
import java.util.Base64

object CryptoUtils {
    private const val KEY = "CRTangarineSecret"

    fun encrypt(plainText: String): String {
        val encrypted = plainText.toByteArray().mapIndexed { index, byte ->
            (byte.toInt() xor KEY[index % KEY.length].code).toByte()
        }.toByteArray()
        return Base64.getEncoder().encodeToString(encrypted)
    }

    fun decrypt(encryptedText: String): String {
        return try {
            val decoded = Base64.getDecoder().decode(encryptedText)
            val decrypted = decoded.mapIndexed { index, byte ->
                (byte.toInt() xor KEY[index % KEY.length].code).toByte()
            }.toByteArray()
            String(decrypted)
        } catch (e: Exception) {
            ""
        }
    }
}


@Serializable
data class AuthTokenPacket(
    val playerUuid: String,
    val encryptedToken: String,
    val assignedStations: List<String>
)

@Serializable
data class BlockData(
    val x: Int,
    val y: Int,
    val z: Int,
    val stateId: Int
)

@Serializable
data class TerrainFrustumPayload(
    val cameraId: String,
    val pitch: Float = 0f,
    val yaw: Float = 0f,
    val blocks: List<BlockData>
)

@Serializable
data class EntityData(
    val id: String,
    val type: String, // e.g. "PLAYER", "MONSTER", "PASSIVE", "ITEM"
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val pitch: Float
)

@Serializable
data class EntityDeltaStream(
    val cameraId: String,
    val entities: List<EntityData>
)

@Serializable
data class CameraStreamCommand(
    val cameraId: String,
    val isActive: Boolean
)

@Serializable
sealed class ModMessage

@Serializable
data class RegistryUpdateMessage(val data: CameraRegistryUpdate) : ModMessage()

@Serializable
data class FrustumPayloadMessage(val data: TerrainFrustumPayload) : ModMessage()

@Serializable
data class EntityStreamMessage(val data: EntityDeltaStream) : ModMessage()
