package me.orange.crtangarine.block

import me.orange.crtangarine.item.SecurityKeycardItem
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
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
            if (currentOwner.isNotEmpty() && currentOwner != player.uuid.toString()) {
                player.displayClientMessage(Component.literal("Access Denied: You do not own this station!"), true)
                return InteractionResult.FAIL
            }

            // Write station position to keycard if player is holding one
            val stack = player.getItemInHand(InteractionHand.MAIN_HAND)
            if (stack.item is SecurityKeycardItem) {
                val customData = stack.get(DataComponents.CUSTOM_DATA) ?: CustomData.EMPTY
                val tag = customData.copyTag()
                tag.putInt("StationX", pos.x)
                tag.putInt("StationY", pos.y)
                tag.putInt("StationZ", pos.z)
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
                player.displayClientMessage(Component.literal("Keycard bound to Station at [${pos.x}, ${pos.y}, ${pos.z}]"), true)
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

    companion object {
        val CODEC: com.mojang.serialization.MapCodec<CameraStationBlock> = simpleCodec(::CameraStationBlock)
    }
}
