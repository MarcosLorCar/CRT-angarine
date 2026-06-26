package me.orange.crtangarine.block

import com.mojang.serialization.MapCodec
import me.orange.crtangarine.item.SecurityKeycardItem
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

class CameraBlock(properties: Properties) : Block(properties), EntityBlock {
    companion object {
        val FACING = BlockStateProperties.FACING
        val CODEC: MapCodec<CameraBlock> = simpleCodec(::CameraBlock)

        private val CEILING_AABB = box(3.0, 6.0, 3.0, 13.0, 16.0, 13.0)
        private val NORTH_AABB = box(3.0, 3.0, 9.0, 13.0, 13.0, 16.0)
        private val SOUTH_AABB = box(3.0, 3.0, 0.0, 13.0, 13.0, 7.0)
        private val EAST_AABB = box(0.0, 3.0, 3.0, 7.0, 13.0, 13.0)
        private val WEST_AABB = box(9.0, 3.0, 3.0, 16.0, 13.0, 13.0)
    }

    init {
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH))
    }

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        return when (state.getValue(FACING)) {
            Direction.DOWN -> CEILING_AABB
            Direction.NORTH -> NORTH_AABB
            Direction.SOUTH -> SOUTH_AABB
            Direction.EAST -> EAST_AABB
            Direction.WEST -> WEST_AABB
            else -> CEILING_AABB
        }
    }

    override fun codec(): MapCodec<CameraBlock> {
        return CODEC
    }

    override fun getRenderShape(state: BlockState): RenderShape {
        return RenderShape.ENTITYBLOCK_ANIMATED
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING)
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState? {
        val clickedFace = context.clickedFace
        if (clickedFace == Direction.UP) {
            return null // Do not allow placing on the ground
        }
        return defaultBlockState().setValue(FACING, clickedFace)
    }

    override fun canSurvive(state: BlockState, level: LevelReader, pos: BlockPos): Boolean {
        val facing = state.getValue(FACING)
        val supportPos = pos.relative(facing.opposite)
        val supportState = level.getBlockState(supportPos)
        if (supportState.`is`(this)) {
            return false // Cannot place cameras on other cameras
        }
        return supportState.isFaceSturdy(level, supportPos, facing)
    }

    override fun updateShape(
        state: BlockState,
        direction: Direction,
        neighborState: BlockState,
        level: LevelAccessor,
        pos: BlockPos,
        neighborPos: BlockPos
    ): BlockState {
        val facing = state.getValue(FACING)
        if (direction == facing.opposite && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState()
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos)
    }

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity {
        return CameraBlockEntity(pos, state)
    }

    override fun useWithoutItem(state: BlockState, level: Level, pos: BlockPos, player: Player, hitResult: BlockHitResult): InteractionResult {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS
        }

        val stack = player.getItemInHand(InteractionHand.MAIN_HAND)
        if (stack.item !is SecurityKeycardItem) {
            player.displayClientMessage(Component.literal("Error: You must hold a Security Keycard bound to a station to interact with this camera!"), true)
            return InteractionResult.FAIL
        }

        val customData = stack.get(DataComponents.CUSTOM_DATA) ?: CustomData.EMPTY
        val tag = customData.copyTag()

        if (!tag.contains("StationX")) {
            player.displayClientMessage(
                Component.literal("Error: Keycard is not bound to any station! Right-click your station first."),
                true
            )
            return InteractionResult.FAIL
        }

        val sx = tag.getInt("StationX")
        val sy = tag.getInt("StationY")
        val sz = tag.getInt("StationZ")
        val stationPos = BlockPos(sx, sy, sz)
        val stationBe = level.getBlockEntity(stationPos) as? CameraStationBlockEntity

        if (stationBe == null) {
            player.displayClientMessage(Component.literal("Error: Linked station at [$sx, $sy, $sz] not found!"), true)
            return InteractionResult.FAIL
        }

        if (stationBe.ownerUuid != player.uuid.toString()) {
            player.displayClientMessage(Component.literal("Error: You do not own the linked station!"), true)
            return InteractionResult.FAIL
        }

        // Distance Check
        val distance = kotlin.math.sqrt(pos.distSqr(stationPos).toDouble())
        val maxDistance = 32.0
        if (distance > maxDistance) {
            val formattedDistance = String.format(java.util.Locale.US, "%.1f", distance)
            val formattedMax = String.format(java.util.Locale.US, "%.1f", maxDistance)
            player.displayClientMessage(
                Component.literal("Error: Camera is too far from the station! (Distance: $formattedDistance, Max Range: $formattedMax)"),
                false
            )
            return InteractionResult.FAIL
        }

        if (stationBe.linkedCameras.contains(pos)) {
            player.displayClientMessage(Component.literal("Camera is already linked to this station!"), true)
            return InteractionResult.CONSUME
        }

        stationBe.linkedCameras.add(pos)
        stationBe.setChanged()
        level.sendBlockUpdated(stationPos, stationBe.blockState, stationBe.blockState, 3)
        CameraStationRegistry.triggerUpdate()
        player.displayClientMessage(Component.literal("Camera successfully linked to Station!"), true)
        return InteractionResult.SUCCESS
    }

    override fun setPlacedBy(level: Level, pos: BlockPos, state: BlockState, placer: LivingEntity?, stack: ItemStack) {
        super.setPlacedBy(level, pos, state, placer, stack)
        val cameraBe = level.getBlockEntity(pos) as? CameraBlockEntity ?: return
        val facing = state.getValue(FACING)
        if (facing == Direction.DOWN) {
            if (placer != null) {
                cameraBe.yaw = placer.yRot + 180.0f
                cameraBe.setChanged()
                level.sendBlockUpdated(pos, state, state, 3)
            }
        } else {
            cameraBe.yaw = facing.toYRot()
            cameraBe.setChanged()
            level.sendBlockUpdated(pos, state, state, 3)
        }
    }

    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, isMoving: Boolean) {
        super.onRemove(state, level, pos, newState, isMoving)
        if (!state.`is`(newState.block)) {
            // Trigger a registry update so the Ktor server and web dashboard know the camera is now offline
            CameraStationRegistry.triggerUpdate()
        }
    }
}
