package me.orange.crtangarine.aim

import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.tick.PlayerTickEvent

object CameraAimEvents {

    @SubscribeEvent
    fun onPlayerTick(event: PlayerTickEvent.Pre) {
        val player = event.entity
        if (player is ServerPlayer) {
            val session = CameraAimManager.sessions[player.uuid]
            if (session != null) {
                val target = session.originalPos
                if (player.distanceToSqr(target.x, target.y, target.z) > 0.001) {
                    player.teleportTo(player.serverLevel(), target.x, target.y, target.z, player.yRot, player.xRot)
                }
            }
        }
    }

    @SubscribeEvent
    fun onPlayerLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        val player = event.entity
        if (player is ServerPlayer) {
            CameraAimManager.sessions.remove(player.uuid)
        }
    }
}
