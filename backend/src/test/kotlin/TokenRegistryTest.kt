package me.orange

import java.io.File
import kotlin.test.*
import me.orange.crtangarine.shared.*


class TokenRegistryTest {

    @BeforeTest
    fun setUp() {
        val file = File("auth_tokens.json")
        if (file.exists()) {
            file.delete()
        }
        TokenRegistry.load()
    }

    @AfterTest
    fun tearDown() {
        val file = File("auth_tokens.json")
        if (file.exists()) {
            file.delete()
        }
    }

    @Test
    fun testRegisterAndGetToken() {
        val uuid = "player-uuid-123"
        val token = "secret-token-xyz"

        TokenRegistry.registerToken(uuid, token, "global")

        assertEquals(token, TokenRegistry.getToken(uuid))
        assertEquals(uuid, TokenRegistry.getPlayerUuid(token))
        assertTrue(TokenRegistry.validateToken(token))
    }

    @Test
    fun testPersistence() {
        val uuid = "persist-uuid"
        val token = "persist-token"

        TokenRegistry.registerToken(uuid, token, "global")

        // Reload from file
        TokenRegistry.load()

        assertEquals(token, TokenRegistry.getToken(uuid))
    }

    @Test
    fun testRemoveToken() {
        val uuid = "remove-uuid"
        val token = "remove-token"

        TokenRegistry.registerToken(uuid, token, "global")
        assertTrue(TokenRegistry.validateToken(token))

        TokenRegistry.removeToken(uuid)
        assertNull(TokenRegistry.getToken(uuid))
        assertFalse(TokenRegistry.validateToken(token))
    }

    @Test
    fun testRegisterFromPacket() {
        val uuid = "packet-player-uuid"
        val rawToken = "super-secret-token"
        val encryptedToken = CryptoUtils.encrypt(rawToken)
        val packet = AuthTokenPacket(
            playerUuid = uuid,
            encryptedToken = encryptedToken,
            assignedStations = emptyList(),
            worldId = "global"
        )

        TokenRegistry.registerFromPacket(packet)

        assertEquals(rawToken, TokenRegistry.getToken(uuid))
        assertTrue(TokenRegistry.validateToken(rawToken))
    }
}
