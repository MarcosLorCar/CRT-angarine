package me.orange

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import me.orange.crtangarine.shared.AuthTokenPacket
import me.orange.crtangarine.shared.CryptoUtils
import kotlinx.serialization.Serializable

@Serializable
data class TokenMetadata(val playerUuid: String, val worldId: String)

object TokenRegistry {
    private val file = File("auth_tokens.json")
    private val tokenMetadataMap = ConcurrentHashMap<String, TokenMetadata>()

    init {
        load()
    }

    @Synchronized
    fun load() {
        try {
            if (file.exists()) {
                val content = file.readText()
                val map = Json.decodeFromString<Map<String, TokenMetadata>>(content)
                tokenMetadataMap.clear()
                tokenMetadataMap.putAll(map)
            }
        } catch (e: Exception) {
            System.err.println("Error loading auth_tokens.json: ${e.message}")
        }
    }

    @Synchronized
    private fun save() {
        try {
            val map = tokenMetadataMap.toMap()
            val content = Json.encodeToString(map)
            file.writeText(content)
        } catch (e: Exception) {
            System.err.println("Error saving auth_tokens.json: ${e.message}")
        }
    }

    fun registerToken(playerUuid: String, token: String, worldId: String) {
        tokenMetadataMap[token] = TokenMetadata(playerUuid, worldId)
        save()
    }

    fun registerFromPacket(packet: AuthTokenPacket) {
        val decryptedToken = CryptoUtils.decrypt(packet.encryptedToken)
        if (decryptedToken.isNotEmpty()) {
            registerToken(packet.playerUuid, decryptedToken, packet.worldId)
        }
    }

    fun getToken(playerUuid: String): String? {
        return tokenMetadataMap.entries.firstOrNull { it.value.playerUuid == playerUuid }?.key
    }

    fun getPlayerUuid(token: String): String? {
        return tokenMetadataMap[token]?.playerUuid
    }

    fun getWorldId(token: String): String? {
        return tokenMetadataMap[token]?.worldId
    }

    fun removeToken(playerUuid: String) {
        tokenMetadataMap.entries.removeIf { it.value.playerUuid == playerUuid }
        save()
    }

    fun validateToken(token: String): Boolean {
        return tokenMetadataMap.containsKey(token)
    }

    fun getAllTokens(): Map<String, TokenMetadata> {
        return tokenMetadataMap.toMap()
    }
}
