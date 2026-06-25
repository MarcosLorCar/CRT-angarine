package me.orange.crtangarine.block

import com.mojang.serialization.MapCodec
import me.orange.crtangarine.item.SecurityKeycardItem
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.phys.BlockHitResult

class CameraBlock(properties: Properties) : HorizontalDirectionalBlock(properties), EntityBlock {
    init {
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH))
    }

    override fun codec(): MapCodec<CameraBlock> {
        return CODEC
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING)
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState? {
        return defaultBlockState().setValue(FACING, context.horizontalDirection.opposite)
    }

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity? {
        return CameraBlockEntity(pos, state)
    }

    override fun useWithoutItem(state: BlockState, level: Level, pos: BlockPos, player: Player, hitResult: BlockHitResult): InteractionResult {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS
        }

        val stack = player.getItemInHand(InteractionHand.MAIN_HAND)
        if (stack.item is SecurityKeycardItem) {
            val customData = stack.get(DataComponents.CUSTOM_DATA) ?: CustomData.EMPTY
            val tag = customData.copyTag()
            if (tag.contains("StationX")) {
                val sx = tag.getInt("StationX")
                val sy = tag.getInt("StationY")
                val sz = tag.getInt("StationZ")
                val stationPos = BlockPos(sx, sy, sz)
                val stationBe = level.getBlockEntity(stationPos) as? CameraStationBlockEntity
                if (stationBe != null) {
                    if (stationBe.ownerUuid == player.uuid.toString()) {
                        if (!stationBe.linkedCameras.contains(pos)) {
                            stationBe.linkedCameras.add(pos)
                            stationBe.setChanged()
                            level.sendBlockUpdated(stationPos, stationBe.blockState, stationBe.blockState, 3)
                            CameraStationRegistry.triggerUpdate()
                            player.displayClientMessage(Component.literal("Camera successfully linked to Station!"), true)
                            return InteractionResult.SUCCESS
                        } else {
                            player.displayClientMessage(Component.literal("Camera is already linked to this station!"), true)
                            return InteractionResult.CONSUME
                        }
                    } else {
                        player.displayClientMessage(Component.literal("Error: You do not own the linked station!"), true)
                        return InteractionResult.FAIL
                    }
                } else {
                    player.displayClientMessage(Component.literal("Error: Linked station at [$sx, $sy, $sz] not found!"), true)
                    return InteractionResult.FAIL
                }
            } else {
                player.displayClientMessage(Component.literal("Error: Keycard is not bound to any station! Right-click your station first."), true)
                return InteractionResult.FAIL
            }
        }

        return InteractionResult.PASS
    }

    companion object {
        val CODEC: MapCodec<CameraBlock> = simpleCodec(::CameraBlock)
    }
}
