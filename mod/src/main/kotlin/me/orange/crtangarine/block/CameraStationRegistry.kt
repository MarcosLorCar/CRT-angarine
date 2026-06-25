package me.orange.crtangarine.block

import java.util.concurrent.ConcurrentHashMap
import net.minecraft.core.BlockPos

object CameraStationRegistry {
    val stations = ConcurrentHashMap<BlockPos, CameraStationBlockEntity>()

    fun register(be: CameraStationBlockEntity) {
        stations[be.blockPos] = be
        triggerUpdate()
    }

    fun unregister(be: CameraStationBlockEntity) {
        stations.remove(be.blockPos)
        triggerUpdate()
    }

    fun triggerUpdate() {
        me.orange.crtangarine.network.CameraStreamingClient.sendRegistryUpdate()
    }
}
