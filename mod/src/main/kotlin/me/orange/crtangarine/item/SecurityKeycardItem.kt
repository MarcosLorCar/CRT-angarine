package me.orange.crtangarine.item

import me.orange.crtangarine.client.openKeycardScreen
import me.orange.crtangarine.shared.AuthTokenPacket
import me.orange.crtangarine.shared.CryptoUtils
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.minecraft.core.component.DataComponents
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.level.Level
import thedarkcolour.kotlinforforge.neoforge.forge.runForDist
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CompletableFuture

class SecurityKeycardItem(properties: Properties) : Item(properties) {

    override fun onCraftedBy(stack: ItemStack, level: Level, player: Player) {
        super.onCraftedBy(stack, level, player)
        if (!level.isClientSide) {
            val token = getDeterministicToken(player)
            bakeIdentity(stack, player, token)
        }
    }

    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        val stack = player.getItemInHand(hand)
        val token = getDeterministicToken(player)

        if (!level.isClientSide) {
            val customData = stack.get(DataComponents.CUSTOM_DATA) ?: CustomData.EMPTY
            val tag = customData.copyTag()
            if (!tag.contains("OwnerUUID") || !tag.contains("NetworkToken")) {
                bakeIdentity(stack, player, token)
            } else {
                // Self-healing: Re-register the token in case the backend server restarted
                registerTokenWithBackend(player.uuid.toString(), token)
            }
        }

        if (level.isClientSide) {
            runForDist(
                clientTarget = { { openKeycardScreen(token) } },
                serverTarget = { { } }
            ).invoke()
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide)
    }

    private fun getDeterministicToken(player: Player): String {
        val input = player.uuid.toString() + "CRTangarineSecretSalt"
        return java.util.UUID.nameUUIDFromBytes(input.toByteArray(Charsets.UTF_8)).toString()
    }

    private fun bakeIdentity(stack: ItemStack, player: Player, token: String) {
        val customData = stack.get(DataComponents.CUSTOM_DATA) ?: CustomData.EMPTY
        val tag = customData.copyTag()
        if (!tag.contains("OwnerUUID")) {
            tag.putString("OwnerUUID", player.uuid.toString())
        }
        if (!tag.contains("NetworkToken")) {
            tag.putString("NetworkToken", token)
            registerTokenWithBackend(player.uuid.toString(), token)
        }
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
    }


    private fun registerTokenWithBackend(playerUuid: String, token: String) {
        CompletableFuture.runAsync {
            try {
                val encrypted = CryptoUtils.encrypt(token)
                val packet = AuthTokenPacket(playerUuid, encrypted, emptyList())
                val body = Json.encodeToString(packet)

                val client = HttpClient.newHttpClient()
                val request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8080/api/register-token"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build()

                val response = client.send(request, HttpResponse.BodyHandlers.ofString())
                if (response.statusCode() != 200) {
                    System.err.println("Failed to register token: ${response.statusCode()} ${response.body()}")
                }
            } catch (e: Exception) {
                System.err.println("Error registering token with backend: ${e.message}")
            }
        }
    }
}
