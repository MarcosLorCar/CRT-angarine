package me.orange.crtangarine.block

import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.LongTag
import net.minecraft.nbt.NumericTag
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.SimpleContainer

class CameraStationBlockEntity(pos: BlockPos, state: BlockState) : BlockEntity(ModBlocks.CAMERA_STATION_BLOCK_ENTITY_TYPE, pos, state) {
    var ownerUuid: String = ""
    var customName: String = ""
    val inventory = SimpleContainer(1)
    val linkedCameras = mutableListOf<BlockPos>()

    override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries)
        tag.putString("OwnerUUID", ownerUuid)
        tag.putString("CustomName", customName)
        tag.put("Inventory", inventory.createTag(registries))

        val listTag = ListTag()
        for (pos in linkedCameras) {
            listTag.add(LongTag.valueOf(pos.asLong()))
        }
        tag.put("LinkedCameras", listTag)
    }

    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(tag, registries)
        ownerUuid = tag.getString("OwnerUUID")
        customName = tag.getString("CustomName")
        inventory.fromTag(tag.getList("Inventory", 10), registries)

        linkedCameras.clear()
        val listTag = tag.getList("LinkedCameras", 4) // 4 is LongTag Type
        for (i in 0 until listTag.size) {
            val tagAtIndex = listTag.get(i)
            if (tagAtIndex is NumericTag) {
                linkedCameras.add(BlockPos.of(tagAtIndex.asLong))
            }
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

    override fun onLoad() {
        super.onLoad()
        if (level != null && !level!!.isClientSide) {
            CameraStationRegistry.register(this)
        }
    }

    override fun setRemoved() {
        super.setRemoved()
        if (level != null && !level!!.isClientSide) {
            CameraStationRegistry.unregister(this)
        }
    }
}
