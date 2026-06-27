package me.orange.crtangarine.item

import me.orange.crtangarine.client.openKeycardScreen
import me.orange.crtangarine.shared.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.ChatFormatting
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
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
        val pos = context.clickedPos
        val blockState = level.getBlockState(pos)

        // 1. Quick exit if it's not our block
        if (!blockState.`is`(me.orange.crtangarine.block.ModBlocks.CAMERA_STATION_BLOCK)) {
            return InteractionResult.PASS
        }

        val player = context.player ?: return InteractionResult.PASS
        val stack = context.itemInHand
        val customData = stack.get(DataComponents.CUSTOM_DATA) ?: CustomData.EMPTY
        val tag = customData.copyTag()
        val ownerUuid = tag.getString("OwnerUUID")

        // 2. Validate Card Activation
        if (ownerUuid.isEmpty()) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.literal("Error: You must activate this keycard by right-clicking in the air first!"), true)
            }
            return InteractionResult.FAIL
        }

        // 3. Validate Card Ownership
        if (ownerUuid != player.uuid.toString()) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.literal("Access Denied: You do not own this keycard!"), true)
            }
            return InteractionResult.FAIL
        }

        // 4. Handle Sneak-Binding
        if (player.isSecondaryUseActive) {
            if (!level.isClientSide) {
                val blockEntity = level.getBlockEntity(pos) as? me.orange.crtangarine.block.CameraStationBlockEntity
                if (blockEntity != null) {
                    val currentOwner = blockEntity.ownerUuid

                    // Validate Station Ownership
                    if (currentOwner.isNotEmpty() && currentOwner != player.uuid.toString()) {
                        player.displayClientMessage(Component.literal("Access Denied: You do not own this station!"), true)
                        return InteractionResult.FAIL
                    }

                    // Bind coordinates to the card
                    tag.putInt("StationX", pos.x)
                    tag.putInt("StationY", pos.y)
                    tag.putInt("StationZ", pos.z)
                    stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
                    player.displayClientMessage(Component.literal("Keycard bound to Station at [${pos.x}, ${pos.y}, ${pos.z}]"), true)
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide)
        }

        // If they right-clicked the station with a valid card but AREN'T sneaking,
        // let it PASS so the block's useWithoutItem can trigger and open the GUI
        return InteractionResult.PASS
    }

    override fun appendHoverText(stack: ItemStack, context: TooltipContext, tooltip: MutableList<Component>, flag: TooltipFlag) {
        val customData = stack.get(DataComponents.CUSTOM_DATA) ?: CustomData.EMPTY
        val tag = customData.copyTag()
        if (tag.contains("StationX") && tag.contains("StationY") && tag.contains("StationZ")) {
            val x = tag.getInt("StationX")
            val y = tag.getInt("StationY")
            val z = tag.getInt("StationZ")
            tooltip.add(Component.literal("linking station $x, $y, $z").withStyle(ChatFormatting.GREEN))
        }
        super.appendHoverText(stack, context, tooltip, flag)
    }

    override fun onCraftedBy(stack: ItemStack, level: Level, player: Player) {
        super.onCraftedBy(stack, level, player)
        if (!level.isClientSide) {
            val worldId = me.orange.crtangarine.world.WorldIdSavedData.get(level as net.minecraft.server.level.ServerLevel).worldId
            val token = getDeterministicToken(player, worldId)
            bakeIdentity(stack, player, token, worldId)
        }
    }

    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        val stack = player.getItemInHand(hand)

        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide)
        }

        val customData = stack.get(DataComponents.CUSTOM_DATA) ?: CustomData.EMPTY
        val tag = customData.copyTag()
        val ownerUuid = tag.getString("OwnerUUID")

        if (ownerUuid.isNotEmpty() && ownerUuid != player.uuid.toString()) {
            player.displayClientMessage(Component.literal("Access Denied: You do not own this keycard!"), true)
            return InteractionResultHolder.fail(stack)
        }

        val worldId = me.orange.crtangarine.world.WorldIdSavedData.get(level as net.minecraft.server.level.ServerLevel).worldId
        val token = getDeterministicToken(player, worldId)

        if (!tag.contains("OwnerUUID") || !tag.contains("NetworkToken")) {
            bakeIdentity(stack, player, token, worldId)
        } else {
            // Self-healing: Re-register the token in case the backend server restarted
            registerTokenWithBackend(player.uuid.toString(), token, worldId)
        }

        if (player is net.minecraft.server.level.ServerPlayer) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, me.orange.crtangarine.network.OpenKeycardScreenPayload(token))
        }

        return InteractionResultHolder.success(stack)
    }

    private fun getDeterministicToken(player: Player, worldId: String): String {
        val input = player.uuid.toString() + worldId + "CRTangarineSecretSalt"
        return java.util.UUID.nameUUIDFromBytes(input.toByteArray(Charsets.UTF_8)).toString()
    }

    private fun bakeIdentity(stack: ItemStack, player: Player, token: String, worldId: String) {
        val customData = stack.get(DataComponents.CUSTOM_DATA) ?: CustomData.EMPTY
        val tag = customData.copyTag()
        if (!tag.contains("OwnerUUID")) {
            tag.putString("OwnerUUID", player.uuid.toString())
        }
        if (!tag.contains("NetworkToken")) {
            tag.putString("NetworkToken", token)
            registerTokenWithBackend(player.uuid.toString(), token, worldId)
        }
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
    }


    private fun registerTokenWithBackend(playerUuid: String, token: String, worldId: String) {
        CompletableFuture.runAsync {
            try {
                val encrypted = CryptoUtils.encrypt(token)
                val packet = AuthTokenPacket(
                    playerUuid = playerUuid,
                    encryptedToken = encrypted,
                    assignedStations = emptyList(),
                    worldId = worldId
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
