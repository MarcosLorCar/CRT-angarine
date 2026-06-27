package me.orange.crtangarine.aim

import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent

object CameraAimEvents {

    @SubscribeEvent
    fun onPlayerLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        val player = event.entity
        if (player is ServerPlayer) {
            CameraAimManager.sessions.remove(player.uuid)
        }
    }
}
