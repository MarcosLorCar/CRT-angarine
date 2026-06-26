package me.orange.crtangarine.block

import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.core.Direction
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

class CameraBlockEntity(pos: BlockPos, state: BlockState) : BlockEntity(ModBlocks.CAMERA_BLOCK_ENTITY_TYPE, pos, state) {
    var pitch: Float = 45.0f
    var yaw: Float = run {
        val facing = if (state.hasProperty(CameraBlock.FACING)) {
            state.getValue(CameraBlock.FACING)
        } else {
            Direction.NORTH
        }
        facing.toYRot()
    }

    override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries)
        tag.putFloat("Pitch", pitch)
        tag.putFloat("Yaw", yaw)
    }

    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(tag, registries)
        if (tag.contains("Pitch")) {
            pitch = tag.getFloat("Pitch")
        }
        if (tag.contains("Yaw")) {
            yaw = tag.getFloat("Yaw")
        }
    }

    override fun getUpdatePacket(): net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener>? {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this)
    }

    override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag {
        val tag = CompoundTag()
        saveAdditional(tag, registries)
        return tag
    }
}
