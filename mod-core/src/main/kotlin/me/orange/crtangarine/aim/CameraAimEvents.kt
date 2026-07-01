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
                // Freeze player rotation on the server so other players see them looking still at the console
                player.yRot = session.originalYaw
                player.xRot = session.originalPitch
                player.yRotO = session.originalYaw
                player.xRotO = session.originalPitch
                player.yHeadRot = session.originalYaw
                player.yHeadRotO = session.originalYaw
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
