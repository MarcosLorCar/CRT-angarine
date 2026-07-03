package me.orange

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import me.orange.crtangarine.shared.AuthTokenPacket
import me.orange.crtangarine.shared.CryptoUtils
import java.security.MessageDigest

data class TokenMetadata(val playerUuid: String, val worldId: String)

data class PlayerAuthData(
    val playerUuid: String,
    val passwordHash: String,
    val worldId: String,
    val playerName: String
)

object TokenRegistry {
    private val file = File("data/auth_tokens.json")
    private val sessionsFile = File("data/active_sessions.json")
    private val playerAuthMap = ConcurrentHashMap<String, PlayerAuthData>()
    private val activeSessions = ConcurrentHashMap<String, TokenMetadata>()
    private val gson = Gson()

    init {
        load()
    }

    @Synchronized
    fun load() {
        try {
            if (file.exists()) {
                val content = file.readText()
                val type = object : TypeToken<Map<String, PlayerAuthData>>() {}.type
                val map = gson.fromJson<Map<String, PlayerAuthData>>(content, type)
                playerAuthMap.clear()
                if (map != null) {
                    playerAuthMap.putAll(map)
                }
            }
            if (sessionsFile.exists()) {
                val content = sessionsFile.readText()
                val type = object : TypeToken<Map<String, TokenMetadata>>() {}.type
                val map = gson.fromJson<Map<String, TokenMetadata>>(content, type)
                activeSessions.clear()
                if (map != null) {
                    activeSessions.putAll(map)
                }
            }
        } catch (e: Exception) {
            System.err.println("Error loading auth files: ${e.message}")
        }
    }

    @Synchronized
    private fun save() {
        try {
            file.parentFile?.mkdirs()
            val map = playerAuthMap.toMap()
            val content = gson.toJson(map)
            file.writeText(content)

            val sessionsMap = activeSessions.toMap()
            val sessionsContent = gson.toJson(sessionsMap)
            sessionsFile.writeText(sessionsContent)
        } catch (e: Exception) {
            System.err.println("Error saving auth files: ${e.message}")
        }
    }

    fun registerToken(playerUuid: String, token: String, worldId: String) {
        // Compatibility method (mainly for tests)
        val hash = hashPassword(token)
        val playerName = "player_${playerUuid.take(6)}"
        playerAuthMap[playerName.lowercase()] = PlayerAuthData(playerUuid, hash, worldId, playerName)
        // Also register as active session for test assertion compatibility
        activeSessions[token] = TokenMetadata(playerUuid, worldId)
        save()
    }

    fun registerFromPacket(packet: AuthTokenPacket) {
        val decryptedPassword = CryptoUtils.decrypt(packet.encryptedToken)
        if (decryptedPassword.isNotEmpty()) {
            val username = if (packet.playerName.isNotEmpty()) packet.playerName else "player_${packet.playerUuid.take(6)}"
            val hash = hashPassword(decryptedPassword)
            playerAuthMap[username.lowercase()] = PlayerAuthData(packet.playerUuid, hash, packet.worldId, username)
            // Also register as active session for test and legacy compatibility
            activeSessions[decryptedPassword] = TokenMetadata(packet.playerUuid, packet.worldId)
            save()
        }
    }

    fun getToken(playerUuid: String): String? {
        return activeSessions.entries.firstOrNull { it.value.playerUuid == playerUuid }?.key
    }

    fun getPlayerUuid(token: String): String? {
        return activeSessions[token]?.playerUuid
    }

    fun getWorldId(token: String): String? {
        return activeSessions[token]?.worldId
    }

    fun removeToken(playerUuid: String) {
        playerAuthMap.entries.removeIf { it.value.playerUuid == playerUuid }
        activeSessions.entries.removeIf { it.value.playerUuid == playerUuid }
        save()
    }

    fun validateToken(token: String): Boolean {
        return activeSessions.containsKey(token)
    }

    fun getAllTokens(): Map<String, TokenMetadata> {
        return activeSessions.toMap()
    }

    fun hashPassword(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(password.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    fun validateCredentials(username: String, password: String): PlayerAuthData? {
        val auth = playerAuthMap[username.lowercase()] ?: return null
        val hash = hashPassword(password)
        return if (hash == auth.passwordHash) auth else null
    }

    fun hasUser(username: String): Boolean {
        return playerAuthMap.containsKey(username.lowercase())
    }

    fun createSession(username: String, playerUuid: String, worldId: String): String {
        val sessionToken = java.util.UUID.randomUUID().toString()
        activeSessions[sessionToken] = TokenMetadata(playerUuid, worldId)
        save()
        return sessionToken
    }
}
