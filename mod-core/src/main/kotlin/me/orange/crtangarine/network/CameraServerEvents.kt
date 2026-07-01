package me.orange.crtangarine.network

import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.server.ServerStartingEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent

object CameraServerEvents {
    @SubscribeEvent
    fun onServerStarting(event: ServerStartingEvent) {
        CameraStreamingClient.start(event.server)
    }

    @SubscribeEvent
    fun onServerStopping(event: ServerStoppingEvent) {
        CameraStreamingClient.stop()
    }
}
