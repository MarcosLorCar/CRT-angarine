package me.orange.crtangarine.aim

import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.tick.PlayerTickEvent

object CameraAimEvents {

    @SubscribeEvent
    fun onPlayerTick(event: PlayerTickEvent.Pre) {
        val player = event.entity
        if (player is ServerPlayer) {
            val session = CameraAimManager.sessions[player.uuid]
            if (session != null) {
                val pos = session.cameraPos
                val targetX = pos.x + 0.5
                val targetY = pos.y + 0.5 - player.eyeHeight
                val targetZ = pos.z + 0.5
                if (player.distanceToSqr(targetX, targetY, targetZ) > 0.001) {
                    player.teleportTo(player.serverLevel(), targetX, targetY, targetZ, player.yRot, player.xRot)
                }
            }
        }
    }
}
