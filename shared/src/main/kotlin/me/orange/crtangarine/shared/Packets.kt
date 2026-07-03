package me.orange.crtangarine.shared

import kotlinx.serialization.Serializable
import java.util.Base64

object CryptoUtils {
    private const val ALGORITHM = "AES"
    private val KEY_BYTES = "CRTangarineSecr1".toByteArray(Charsets.UTF_8)

    fun encrypt(plainText: String): String {
        return try {
            val keySpec = javax.crypto.spec.SecretKeySpec(KEY_BYTES, ALGORITHM)
            val cipher = javax.crypto.Cipher.getInstance(ALGORITHM)
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, keySpec)
            val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            Base64.getEncoder().encodeToString(encrypted)
        } catch (e: Exception) {
            ""
        }
    }

    fun decrypt(encryptedText: String): String {
        return try {
            val keySpec = javax.crypto.spec.SecretKeySpec(KEY_BYTES, ALGORITHM)
            val cipher = javax.crypto.Cipher.getInstance(ALGORITHM)
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, keySpec)
            val decoded = Base64.getDecoder().decode(encryptedText)
            val decrypted = cipher.doFinal(decoded)
            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }
}


@Serializable
data class AuthTokenPacket(
    val playerUuid: String,
    val encryptedToken: String,
    val assignedStations: List<String>,
    val worldId: String = "global",
    val playerName: String = ""
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
