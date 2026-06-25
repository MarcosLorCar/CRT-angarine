package me.orange

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import me.orange.crtangarine.shared.AuthTokenPacket
import me.orange.crtangarine.shared.CryptoUtils


object TokenRegistry {
    private val file = File("auth_tokens.json")
    private val tokens = ConcurrentHashMap<String, String>()

    init {
        load()
    }

    @Synchronized
    fun load() {
        try {
            if (file.exists()) {
                val content = file.readText()
                val map = Json.decodeFromString<Map<String, String>>(content)
                tokens.clear()
                tokens.putAll(map)
            }
        } catch (e: Exception) {
            System.err.println("Error loading auth_tokens.json: ${e.message}")
        }
    }

    @Synchronized
    private fun save() {
        try {
            val map = tokens.toMap()
            val content = Json.encodeToString(map)
            file.writeText(content)
        } catch (e: Exception) {
            System.err.println("Error saving auth_tokens.json: ${e.message}")
        }
    }

    fun registerToken(playerUuid: String, token: String) {
        tokens[playerUuid] = token
        save()
    }

    fun registerFromPacket(packet: AuthTokenPacket) {
        val decryptedToken = CryptoUtils.decrypt(packet.encryptedToken)
        if (decryptedToken.isNotEmpty()) {
            registerToken(packet.playerUuid, decryptedToken)
        }
    }


    fun getToken(playerUuid: String): String? {
        return tokens[playerUuid]
    }

    fun getPlayerUuid(token: String): String? {
        return tokens.entries.firstOrNull { it.value == token }?.key
    }

    fun removeToken(playerUuid: String) {
        tokens.remove(playerUuid)
        save()
    }

    fun validateToken(token: String): Boolean {
        return tokens.values.contains(token)
    }

    fun getAllTokens(): Map<String, String> {
        return tokens.toMap()
    }
}
