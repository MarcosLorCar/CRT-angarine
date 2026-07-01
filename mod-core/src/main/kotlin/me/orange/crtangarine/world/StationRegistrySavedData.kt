package me.orange.crtangarine.world

import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.LongTag
import net.minecraft.nbt.NumericTag
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.saveddata.SavedData
import java.util.concurrent.ConcurrentHashMap

class StationRegistrySavedData : SavedData() {
    data class StationEntry(
        val pos: BlockPos,
        val dimension: ResourceKey<Level>,
        val ownerUuid: String,
        val customName: String,
        val linkedCameras: List<BlockPos>
    )

    private val stations = ConcurrentHashMap<BlockPos, StationEntry>()

    fun getStations(): Collection<StationEntry> {
        return stations.values
    }

    fun addOrUpdateStation(pos: BlockPos, dimension: ResourceKey<Level>, ownerUuid: String, customName: String, linkedCameras: List<BlockPos>) {
        stations[pos] = StationEntry(pos, dimension, ownerUuid, customName, linkedCameras.toList())
        setDirty()
    }

    fun removeStation(pos: BlockPos) {
        if (stations.remove(pos) != null) {
            setDirty()
        }
    }

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        val listTag = ListTag()
        for (entry in stations.values) {
            val stationTag = CompoundTag()
            stationTag.putInt("X", entry.pos.x)
            stationTag.putInt("Y", entry.pos.y)
            stationTag.putInt("Z", entry.pos.z)
            stationTag.putString("Dimension", entry.dimension.location().toString())
            stationTag.putString("OwnerUUID", entry.ownerUuid)
            stationTag.putString("CustomName", entry.customName)

            val camerasList = ListTag()
            for (camPos in entry.linkedCameras) {
                camerasList.add(LongTag.valueOf(camPos.asLong()))
            }
            stationTag.put("LinkedCameras", camerasList)
            listTag.add(stationTag)
        }
        tag.put("Stations", listTag)
        return tag
    }

    companion object {
        private val FACTORY = Factory(
            { StationRegistrySavedData() },
            { tag, registries ->
                val data = StationRegistrySavedData()
                val listTag = tag.getList("Stations", 10) // 10 is CompoundTag
                for (i in 0 until listTag.size) {
                    val stationTag = listTag.getCompound(i)
                    val pos = BlockPos(
                        stationTag.getInt("X"),
                        stationTag.getInt("Y"),
                        stationTag.getInt("Z")
                    )
                    val dimStr = stationTag.getString("Dimension")
                    val dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dimStr))
                    val ownerUuid = stationTag.getString("OwnerUUID")
                    val customName = stationTag.getString("CustomName")

                    val linkedCameras = mutableListOf<BlockPos>()
                    val camerasList = stationTag.getList("LinkedCameras", 4) // 4 is LongTag
                    for (j in 0 until camerasList.size) {
                        val tagAtIndex = camerasList.get(j)
                        if (tagAtIndex is NumericTag) {
                            linkedCameras.add(BlockPos.of(tagAtIndex.asLong))
                        }
                    }
                    data.stations[pos] = StationEntry(pos, dimension, ownerUuid, customName, linkedCameras)
                }
                data
            },
            null
        )

        fun get(level: ServerLevel): StationRegistrySavedData {
            val storage = level.server.overworld().dataStorage
            return storage.computeIfAbsent(FACTORY, "crtangarine_station_registry")
        }
    }
}
