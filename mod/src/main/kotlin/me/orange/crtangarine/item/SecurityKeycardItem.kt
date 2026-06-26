package me.orange.crtangarine.item

import me.orange.crtangarine.client.openKeycardScreen
import me.orange.crtangarine.shared.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import net.minecraft.core.component.DataComponents
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.Level
import thedarkcolour.kotlinforforge.neoforge.forge.runForDist
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CompletableFuture

class SecurityKeycardItem(properties: Properties) : Item(properties) {

    override fun useOn(context: UseOnContext): InteractionResult {
        val level = context.level
        val player = context.player ?: return InteractionResult.PASS
        val pos = context.clickedPos
        val blockState = level.getBlockState(pos)

        if (blockState.`is`(me.orange.crtangarine.block.ModBlocks.CAMERA_STATION_BLOCK)) {
            if (player.isSecondaryUseActive) {
                if (!level.isClientSide) {
                    val blockEntity = level.getBlockEntity(pos) as? me.orange.crtangarine.block.CameraStationBlockEntity
                    if (blockEntity != null) {
                        val currentOwner = blockEntity.ownerUuid
                        if (currentOwner.isNotEmpty() && currentOwner != player.uuid.toString()) {
                            player.displayClientMessage(net.minecraft.network.chat.Component.literal("Access Denied: You do not own this station!"), true)
                            return InteractionResult.FAIL
                        }
                        
                        val stack = context.itemInHand
                        val customData = stack.get(DataComponents.CUSTOM_DATA) ?: CustomData.EMPTY
                        val tag = customData.copyTag()
                        tag.putInt("StationX", pos.x)
                        tag.putInt("StationY", pos.y)
                        tag.putInt("StationZ", pos.z)
                        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
                        player.displayClientMessage(net.minecraft.network.chat.Component.literal("Keycard bound to Station at [${pos.x}, ${pos.y}, ${pos.z}]"), true)
                    }
                }
                return InteractionResult.sidedSuccess(level.isClientSide)
            }
        }
        return InteractionResult.PASS
    }

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
                val packet = AuthTokenPacket(
                    playerUuid = playerUuid,
                    encryptedToken = encrypted,
                    assignedStations = emptyList()
                )
                val body = kotlinx.serialization.json.Json.encodeToString(packet)

                val backendUri = me.orange.crtangarine.network.ModConfiguration.CONFIG.backendUri.get()
                val client = HttpClient.newHttpClient()
                val request = HttpRequest.newBuilder()
                    .uri(URI.create("http://$backendUri/api/register-token"))
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
