package me.orange.crtangarine.block

import me.orange.crtangarine.item.SecurityKeycardItem
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.SimpleMenuProvider

class CameraStationBlock(properties: Properties) : Block(properties), EntityBlock {
    override fun codec(): com.mojang.serialization.MapCodec<CameraStationBlock> {
        return CODEC
    }

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity? {
        return CameraStationBlockEntity(pos, state)
    }

    override fun useWithoutItem(state: BlockState, level: Level, pos: BlockPos, player: Player, hitResult: BlockHitResult): InteractionResult {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS
        }

        val blockEntity = level.getBlockEntity(pos) as? CameraStationBlockEntity
        if (blockEntity != null) {
            val currentOwner = blockEntity.ownerUuid
            val stack = player.getItemInHand(InteractionHand.MAIN_HAND)

            // Sneaking (Shift-clicking) with a Keycard binds the station to the keycard
            if (player.isSecondaryUseActive && stack.item is SecurityKeycardItem) {
                if (currentOwner.isNotEmpty() && currentOwner != player.uuid.toString()) {
                    player.displayClientMessage(Component.literal("Access Denied: You do not own this station!"), true)
                    return InteractionResult.FAIL
                }
                
                val customData = stack.get(DataComponents.CUSTOM_DATA) ?: CustomData.EMPTY
                val tag = customData.copyTag()
                tag.putInt("StationX", pos.x)
                tag.putInt("StationY", pos.y)
                tag.putInt("StationZ", pos.z)
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
                player.displayClientMessage(Component.literal("Keycard bound to Station at [${pos.x}, ${pos.y}, ${pos.z}]"), true)
                return InteractionResult.SUCCESS
            }

            // Normal right-click interaction opens the UI (if owned by the player or ownerless)
            if (currentOwner.isNotEmpty() && currentOwner != player.uuid.toString()) {
                player.displayClientMessage(Component.literal("Access Denied: You do not own this station!"), true)
                return InteractionResult.FAIL
            }

            // Open menu and write BlockPos
            player.openMenu(SimpleMenuProvider(
                { windowId, playerInv, _ -> CameraStationMenu(windowId, playerInv, pos) },
                Component.literal("Camera Station")
            )) { buf ->
                buf.writeBlockPos(pos)
            }
        }

        return InteractionResult.CONSUME
    }

    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, isMoving: Boolean) {
        if (!state.`is`(newState.block)) {
            val blockEntity = level.getBlockEntity(pos) as? CameraStationBlockEntity
            if (blockEntity != null) {
                net.minecraft.world.Containers.dropContents(level, pos, blockEntity.inventory)
            }
            super.onRemove(state, level, pos, newState, isMoving)
        }
    }

    companion object {
        val CODEC: com.mojang.serialization.MapCodec<CameraStationBlock> = simpleCodec(::CameraStationBlock)
    }
}
