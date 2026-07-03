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

        val ownerName = tag.getString("OwnerName")
        if (ownerName.isNotEmpty()) {
            tooltip.add(Component.literal("Owner: $ownerName").withStyle(ChatFormatting.BLUE))
        } else if (tag.contains("OwnerUUID")) {
            tooltip.add(Component.literal("Owner UUID: " + tag.getString("OwnerUUID")).withStyle(ChatFormatting.DARK_GRAY))
        }

        if (tag.contains("StationX") && tag.contains("StationY") && tag.contains("StationZ")) {
            val x = tag.getInt("StationX")
            val y = tag.getInt("StationY")
            val z = tag.getInt("StationZ")
            tooltip.add(Component.literal("Linking Station at [$x, $y, $z]").withStyle(ChatFormatting.GREEN))
        }
        super.appendHoverText(stack, context, tooltip, flag)
    }

    override fun isFoil(stack: ItemStack): Boolean {
        val customData = stack.get(DataComponents.CUSTOM_DATA) ?: CustomData.EMPTY
        val tag = customData.copyTag()
        return tag.contains("StationX") && tag.contains("StationY") && tag.contains("StationZ")
    }

    override fun inventoryTick(stack: ItemStack, level: Level, entity: net.minecraft.world.entity.Entity, slotId: Int, isSelected: Boolean) {
        if (!level.isClientSide && entity is Player) {
            val customData = stack.get(DataComponents.CUSTOM_DATA) ?: CustomData.EMPTY
            val tag = customData.copyTag()
            if (tag.contains("StationX")) {
                val isHolding = isSelected || entity.getItemInHand(InteractionHand.OFF_HAND) === stack
                if (!isHolding) {
                    tag.remove("StationX")
                    tag.remove("StationY")
                    tag.remove("StationZ")
                    stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
                    entity.displayClientMessage(Component.literal("Linking aborted: You stopped holding the keycard!"), true)
                }
            }
        }
        super.inventoryTick(stack, level, entity, slotId, isSelected)
    }

    override fun onCraftedBy(stack: ItemStack, level: Level, player: Player) {
        super.onCraftedBy(stack, level, player)
        // Let the player activate it on right-click to enter their password
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

        if (!tag.contains("OwnerUUID") || !tag.contains("NetworkToken")) {
            // Trigger client screen to configure password by sending an empty token
            if (player is net.minecraft.server.level.ServerPlayer) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, me.orange.crtangarine.network.OpenKeycardScreenPayload(""))
            }
        } else {
            val token = tag.getString("NetworkToken")
            val ownerName = tag.getString("OwnerName")
            // Self-healing: Re-register the token in case the backend server restarted
            registerTokenWithBackend(player.uuid.toString(), token, worldId, ownerName)
            if (player is net.minecraft.server.level.ServerPlayer) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, me.orange.crtangarine.network.OpenKeycardScreenPayload(token))
            }
        }

        return InteractionResultHolder.success(stack)
    }

    fun activateWithPassword(stack: ItemStack, player: Player, password: String, worldId: String) {
        val customData = stack.get(DataComponents.CUSTOM_DATA) ?: CustomData.EMPTY
        val tag = customData.copyTag()
        val encryptedPassword = CryptoUtils.encrypt(password)

        tag.putString("OwnerUUID", player.uuid.toString())
        tag.putString("OwnerName", player.scoreboardName)
        tag.putString("NetworkToken", encryptedPassword)
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))

        // Register token with backend
        registerTokenWithBackend(player.uuid.toString(), encryptedPassword, worldId, player.scoreboardName)

        if (player is net.minecraft.server.level.ServerPlayer) {
            player.displayClientMessage(Component.literal("Keycard security activated successfully!"), true)
            // Open normal keycard screen now that it is registered
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, me.orange.crtangarine.network.OpenKeycardScreenPayload(encryptedPassword))
        }
    }

    private fun registerTokenWithBackend(playerUuid: String, token: String, worldId: String, playerName: String) {
        CompletableFuture.runAsync {
            try {
                val packet = AuthTokenPacket(
                    playerUuid = playerUuid,
                    encryptedToken = token, // Already encrypted
                    assignedStations = emptyList(),
                    worldId = worldId,
                    playerName = playerName
                )
                val body = kotlinx.serialization.json.Json.encodeToString(packet)

                val backendUri = me.orange.crtangarine.network.ModConfiguration.getEffectiveBackendUri()
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
