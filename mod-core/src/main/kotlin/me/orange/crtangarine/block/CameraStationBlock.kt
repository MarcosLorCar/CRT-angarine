package me.orange.crtangarine.block

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
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.block.state.properties.DirectionProperty
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.phys.shapes.VoxelShape
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.level.BlockGetter

class CameraStationBlock(properties: Properties) : Block(properties), EntityBlock {
    init {
        registerDefaultState(
            stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(OWNED, false)
        )
    }

    override fun codec(): com.mojang.serialization.MapCodec<CameraStationBlock> {
        return CODEC
    }

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity? {
        return CameraStationBlockEntity(pos, state)
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING, OWNED)
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState? {
        return defaultBlockState().setValue(FACING, context.horizontalDirection.opposite)
    }

    override fun useWithoutItem(state: BlockState, level: Level, pos: BlockPos, player: Player, hitResult: BlockHitResult): InteractionResult {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS
        }

        val blockEntity = level.getBlockEntity(pos) as? CameraStationBlockEntity
        if (blockEntity != null) {
            val currentOwner = blockEntity.ownerUuid

            // Prevent unauthorized players from opening the GUI
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
            return InteractionResult.SUCCESS
        }

        return InteractionResult.CONSUME
    }

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        return when (state.getValue(FACING)) {
            Direction.NORTH -> NORTH_SHAPE
            Direction.SOUTH -> SOUTH_SHAPE
            Direction.EAST -> EAST_SHAPE
            Direction.WEST -> WEST_SHAPE
            else -> NORTH_SHAPE
        }
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
        val FACING: DirectionProperty = HorizontalDirectionalBlock.FACING
        val OWNED: BooleanProperty = BooleanProperty.create("owned")
        val CODEC: com.mojang.serialization.MapCodec<CameraStationBlock> = simpleCodec(::CameraStationBlock)

        private val BASE_SHAPE = box(0.0, 0.0, 0.0, 16.0, 8.0, 16.0)

        private val NORTH_SHAPE = Shapes.or(
            BASE_SHAPE,
            box(3.0, 8.0, 3.0, 13.0, 9.0, 7.0),
            box(4.0, 8.0, 10.0, 12.0, 16.0, 12.0)
        )

        private val SOUTH_SHAPE = Shapes.or(
            BASE_SHAPE,
            box(3.0, 8.0, 9.0, 13.0, 9.0, 13.0),
            box(4.0, 8.0, 4.0, 12.0, 16.0, 6.0)
        )

        private val EAST_SHAPE = Shapes.or(
            BASE_SHAPE,
            box(9.0, 8.0, 3.0, 13.0, 9.0, 13.0),
            box(4.0, 8.0, 4.0, 6.0, 16.0, 12.0)
        )

        private val WEST_SHAPE = Shapes.or(
            BASE_SHAPE,
            box(3.0, 8.0, 3.0, 7.0, 9.0, 13.0),
            box(10.0, 8.0, 4.0, 12.0, 16.0, 12.0)
        )
    }
}
