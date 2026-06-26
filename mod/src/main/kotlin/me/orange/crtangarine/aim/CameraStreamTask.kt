package me.orange.crtangarine.aim

import me.orange.crtangarine.network.CameraStreamingClient
import me.orange.crtangarine.shared.*
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.entity.animal.Animal
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import java.util.concurrent.ConcurrentHashMap

object CameraStreamTask {
    private var tickCount = 0

    // Cache to prevent sending frustum block data every tick. Map of cameraPos -> lastSentTick
    private val lastFrustumSent = ConcurrentHashMap<BlockPos, Int>()

    @SubscribeEvent
    fun onServerTick(event: ServerTickEvent.Post) {
        val server = event.server
        tickCount++

        // Process every 2 ticks (10 times a second) for entity updates
        if (tickCount % 2 != 0) return

        val activeCameras = CameraStreamingClient.activeStreamingCameras.keys()
        if (!activeCameras.hasMoreElements()) return

        // Get server level(s)
        val levels = server.allLevels

        for (camPos in activeCameras) {
            // Find which level contains this camera and has it loaded
            var cameraLevel: ServerLevel? = null
            for (level in levels) {
                if (level.isLoaded(camPos)) {
                    cameraLevel = level
                    break
                }
            }
            if (cameraLevel == null) continue

            val cameraBe = cameraLevel.getBlockEntity(camPos) as? me.orange.crtangarine.block.CameraBlockEntity ?: continue

            // Math projection setup
            val pitch = cameraBe.pitch
            val yaw = cameraBe.yaw

            // Calculate look direction vector
            val f = pitch * (Math.PI.toFloat() / 180f)
            val g = -yaw * (Math.PI.toFloat() / 180f)
            val h = Math.cos(g.toDouble()).toFloat()
            val i = Math.sin(g.toDouble()).toFloat()
            val j = Math.cos(f.toDouble()).toFloat()
            val k = Math.sin(f.toDouble()).toFloat()
            val lookDir = Vec3((i * j).toDouble(), (-k).toDouble(), (h * j).toDouble())

            val camCenter = Vec3(camPos.x + 0.5, camPos.y + 0.5, camPos.z + 0.5)
            val cameraId = "${camPos.x},${camPos.y},${camPos.z}"

            // 1. Terrain Frustum Blocks (send once every 40 ticks / 2 seconds)
            val lastSent = lastFrustumSent[camPos] ?: 0
            if (tickCount - lastSent >= 40 || lastSent == 0) {
                lastFrustumSent[camPos] = tickCount
                sendFrustumData(cameraLevel, camPos, camCenter, lookDir, cameraId, pitch, yaw)
            }

            // 2. Entity delta stream (every 2 ticks)
            sendEntityData(cameraLevel, camPos, camCenter, lookDir, cameraId)
        }
    }

    private fun sendFrustumData(level: ServerLevel, camPos: BlockPos, camCenter: Vec3, lookDir: Vec3, cameraId: String, pitch: Float, yaw: Float) {
        val blocksList = mutableListOf<BlockData>()
        val radius = 36 // increased from 24 to scale up capture distance
        val minX = camPos.x - radius
        val maxX = camPos.x + radius
        val minY = (camPos.y - radius).coerceAtLeast(level.minBuildHeight)
        val maxY = (camPos.y + radius).coerceAtMost(level.maxBuildHeight)
        val minZ = camPos.z - radius
        val maxZ = camPos.z + radius

        val cosLimit = 0.342f // 70 degree half-angle to cover diagonal corners of 90 degree vertical FOV
        val radiusSq = (radius * radius).toDouble()
        val camCenterX = camCenter.x
        val camCenterY = camCenter.y
        val camCenterZ = camCenter.z
        val lookX = lookDir.x
        val lookY = lookDir.y
        val lookZ = lookDir.z

        for (x in minX..maxX) {
            val dx = (x + 0.5) - camCenterX
            for (y in minY..maxY) {
                val dy = (y + 0.5) - camCenterY
                for (z in minZ..maxZ) {
                    if (x == camPos.x && y == camPos.y && z == camPos.z) continue // Skip camera block

                    val dz = (z + 0.5) - camCenterZ
                    val distSq = dx * dx + dy * dy + dz * dz
                    if (distSq > radiusSq || distSq == 0.0) continue

                    val dist = Math.sqrt(distSq)
                    val dot = (dx * lookX + dy * lookY + dz * lookZ) / dist
                    if (dot >= cosLimit) {
                        val p = BlockPos(x, y, z)
                        val state = level.getBlockState(p)
                        val classId = classifyBlock(state)
                        if (classId != 0) { // Only send non-air/non-passable blocks to keep payload small
                            blocksList.add(
                                BlockData(
                                    x = x,
                                    y = y,
                                    z = z,
                                    stateId = classId
                                )
                            )
                        }
                    }
                }
            }
        }

        CameraStreamingClient.sendFrustumPayload(cameraId, pitch, yaw, blocksList)
    }

    private fun sendEntityData(level: ServerLevel, camPos: BlockPos, camCenter: Vec3, lookDir: Vec3, cameraId: String) {
        val box = AABB(camPos).inflate(36.0)
        val rawEntities = level.getEntities(null, box)
        val entitiesList = mutableListOf<EntityData>()
        val cosLimit = 0.342f // 70 degree half-angle to cover diagonal corners of 90 degree vertical FOV

        for (entity in rawEntities) {
            // Exclude players currently aiming a camera (they are the camera itself)
            if (entity is Player && CameraAimManager.sessions.containsKey(entity.uuid)) {
                continue
            }
            val ep = entity.position()
            val vec = Vec3(ep.x - camCenter.x, ep.y + (entity.bbHeight / 2) - camCenter.y, ep.z - camCenter.z)
            val dist = vec.length()
            if (dist > 36.0 || dist == 0.0) continue

            val dot = vec.dot(lookDir) / dist
            if (dot >= cosLimit) {
                val endVec = Vec3(ep.x, ep.y + (entity.bbHeight / 2.0), ep.z)
                val clipContext = ClipContext(
                    camCenter,
                    endVec,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    entity
                )
                val hitResult = level.clip(clipContext)
                if (hitResult.type == HitResult.Type.BLOCK && hitResult.blockPos != camPos) {
                    continue
                }

                val classification = when (entity) {
                    is Player -> "PLAYER"
                    is Monster -> "MONSTER"
                    is Animal -> "PASSIVE"
                    is ItemEntity -> "ITEM"
                    else -> {
                        if (entity.type.category == MobCategory.MONSTER) "MONSTER" else "PASSIVE"
                    }
                }

                entitiesList.add(
                    EntityData(
                        id = entity.uuid.toString(),
                        type = classification,
                        x = ep.x,
                        y = ep.y,
                        z = ep.z,
                        yaw = entity.yRot,
                        pitch = entity.xRot
                    )
                )
            }
        }

        CameraStreamingClient.sendEntityDeltaStream(cameraId, entitiesList)
    }

    private fun classifyBlock(state: BlockState): Int {
        if (state.isAir) return 0
        val block = state.block

        // Hazard
        if (block is net.minecraft.world.level.block.LiquidBlock) {
            val fluid = state.fluidState.type
            if (fluid.isSame(net.minecraft.world.level.material.Fluids.LAVA) || fluid.isSame(net.minecraft.world.level.material.Fluids.FLOWING_LAVA)) {
                return 2
            }
        }
        if (block is net.minecraft.world.level.block.CampfireBlock ||
            block is net.minecraft.world.level.block.MagmaBlock ||
            block is net.minecraft.world.level.block.WitherRoseBlock) {
            return 2
        }

        // Interactable/Doors
        if (block is net.minecraft.world.level.block.DoorBlock ||
            block is net.minecraft.world.level.block.TrapDoorBlock ||
            block is net.minecraft.world.level.block.ButtonBlock ||
            block is net.minecraft.world.level.block.LeverBlock ||
            block is net.minecraft.world.level.block.ChestBlock ||
            block is net.minecraft.world.level.block.EnderChestBlock ||
            block is net.minecraft.world.level.block.ShulkerBoxBlock) {
            return 3
        }

        // Passable (not solid)
        if (!state.isSolid) {
            return 0
        }

        // Default Solid
        return 1
    }
}
