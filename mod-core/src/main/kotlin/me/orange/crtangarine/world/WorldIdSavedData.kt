package me.orange.crtangarine.world

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.saveddata.SavedData
import java.util.UUID

class WorldIdSavedData : SavedData() {
    var worldId: String = ""

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        tag.putString("WorldID", worldId)
        return tag
    }

    companion object {
        private val FACTORY = Factory(
            { WorldIdSavedData() },
            { tag, registries ->
                val data = WorldIdSavedData()
                data.worldId = tag.getString("WorldID")
                data
            },
            null
        )

        fun get(level: ServerLevel): WorldIdSavedData {
            val storage = level.server.overworld().dataStorage
            val data = storage.computeIfAbsent(FACTORY, "crtangarine_world_id")
            if (data.worldId.isEmpty()) {
                data.worldId = UUID.randomUUID().toString()
                data.setDirty()
            }
            return data
        }
    }
}
