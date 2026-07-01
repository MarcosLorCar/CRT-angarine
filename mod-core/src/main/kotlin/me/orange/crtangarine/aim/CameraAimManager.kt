package me.orange.crtangarine.aim

import net.minecraft.core.BlockPos
import net.minecraft.world.level.GameType
import net.minecraft.world.phys.Vec3
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class AimSession(
    val cameraPos: BlockPos,
    val originalGameMode: GameType,
    val originalPos: Vec3,
    val originalPitch: Float,
    val originalYaw: Float,
    val originalFlying: Boolean
)

object CameraAimManager {
    val sessions = ConcurrentHashMap<UUID, AimSession>() // Player UUID -> AimSession
}
