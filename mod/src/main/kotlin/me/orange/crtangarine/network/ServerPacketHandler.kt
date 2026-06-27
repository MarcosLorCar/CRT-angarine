package me.orange.crtangarine.network

import me.orange.crtangarine.block.CameraStationBlockEntity
import me.orange.crtangarine.block.CameraBlockEntity
import me.orange.crtangarine.block.CameraStationRegistry
import me.orange.crtangarine.aim.CameraAimManager
import me.orange.crtangarine.aim.AimSession
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.phys.Vec3
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component
import net.minecraft.world.level.GameType
import net.neoforged.neoforge.network.handling.IPayloadContext
import net.neoforged.neoforge.network.PacketDistributor

object ServerPacketHandler {
    fun handleUnlinkCamera(payload: UnlinkCameraPayload, context: IPayloadContext) {
        context.enqueueWork {
            val player = context.player()
            val level = player.level()
            val stationPos = payload.stationPos
            val cameraPos = payload.cameraPos
            val blockEntity = level.getBlockEntity(stationPos) as? CameraStationBlockEntity
            if (blockEntity != null) {
                if (blockEntity.ownerUuid == player.uuid.toString()) {
                    if (blockEntity.linkedCameras.remove(cameraPos)) {
                        blockEntity.setChanged()
                        level.sendBlockUpdated(stationPos, blockEntity.blockState, blockEntity.blockState, 3)
                        CameraStationRegistry.triggerUpdate()
                    }
                }
            }
        }
    }

    fun handleUpdateStationName(payload: UpdateStationNamePayload, context: IPayloadContext) {
        context.enqueueWork {
            val player = context.player()
            val level = player.level()
            val pos = payload.pos
            val blockEntity = level.getBlockEntity(pos) as? CameraStationBlockEntity
            if (blockEntity != null) {
                if (blockEntity.ownerUuid.isNotEmpty() && blockEntity.ownerUuid == player.uuid.toString()) {
                    blockEntity.customName = payload.name
                    blockEntity.setChanged()
                    level.sendBlockUpdated(pos, blockEntity.blockState, blockEntity.blockState, 3)
                    CameraStationRegistry.triggerUpdate()
                }
            }
        }
    }

    fun handleAimCamera(payload: AimCameraPayload, context: IPayloadContext) {
        context.enqueueWork {
            val player = context.player() as? ServerPlayer
            val level = player?.serverLevel()
            if (player != null && level != null) {
                val stationPos = payload.stationPos
                val stationBe = level.getBlockEntity(stationPos) as? CameraStationBlockEntity
                if (stationBe == null || stationBe.ownerUuid != player.uuid.toString()) {
                    player.displayClientMessage(Component.literal("Error: You do not own this station!"), true)
                    return@enqueueWork
                }
                val pos = payload.cameraPos
                val cameraBe = level.getBlockEntity(pos) as? CameraBlockEntity
                if (!stationBe.linkedCameras.contains(pos) || cameraBe == null || cameraBe.linkedStationPos != stationPos) {
                    player.displayClientMessage(Component.literal("Error: Camera is not linked to this station!"), true)
                    return@enqueueWork
                }
                val state = level.getBlockState(pos)
                val facing = if (state.hasProperty(me.orange.crtangarine.block.CameraBlock.FACING)) {
                    state.getValue(me.orange.crtangarine.block.CameraBlock.FACING)
                } else if (state.hasProperty(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING)) {
                    state.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING)
                } else {
                    net.minecraft.core.Direction.NORTH
                }

                val yaw = cameraBe?.yaw ?: facing.toYRot()
                val pitch = cameraBe?.pitch ?: 0.0f

                val originalGameMode = player.gameMode.gameModeForPlayer
                val originalPos = player.position()
                val originalPitch = player.xRot
                val originalYaw = player.yRot
                val originalFlying = player.abilities.flying

                // Set game mode to SPECTATOR to enable proper camera movement and hide player model desyncs
                player.setGameMode(GameType.SPECTATOR)

                // Teleport spectator player to the camera block center and rotate to look in camera facing direction
                player.teleportTo(level, pos.x + 0.5, pos.y + 0.5 - player.eyeHeight, pos.z + 0.5, yaw, pitch)

                // Save session
                CameraAimManager.sessions[player.uuid] = AimSession(pos, originalGameMode, originalPos, originalPitch, originalYaw, originalFlying)

                // Tell client to start aim mode click-intercept
                PacketDistributor.sendToPlayer(player, StartAimModePayload(pos))

                player.closeContainer()
            }
        }
    }

    fun handleCommitCameraAim(payload: CommitCameraAimPayload, context: IPayloadContext) {
        context.enqueueWork {
            val player = context.player() as? ServerPlayer
            val level = player?.serverLevel()
            if (player != null && level != null) {
                val session = CameraAimManager.sessions.remove(player.uuid)
                if (session != null) {
                    // Restore game mode
                    player.setGameMode(session.originalGameMode)
                    player.abilities.flying = session.originalFlying
                    player.onUpdateAbilities()
                    // Teleport back to console
                    player.teleportTo(level, session.originalPos.x, session.originalPos.y, session.originalPos.z, session.originalYaw, session.originalPitch)
                }

                // Save yaw/pitch to camera block entity
                val cameraPos = payload.cameraPos
                val blockEntity = level.getBlockEntity(cameraPos) as? CameraBlockEntity
                if (blockEntity != null) {
                    blockEntity.pitch = payload.pitch
                    blockEntity.yaw = payload.yaw
                    blockEntity.setChanged()
                    level.sendBlockUpdated(cameraPos, blockEntity.blockState, blockEntity.blockState, 3)
                }
            }
        }
    }

    fun handleLocateCamera(payload: LocateCameraPayload, context: IPayloadContext) {
        context.enqueueWork {
            val player = context.player() as? ServerPlayer
            val level = player?.serverLevel()
            if (player != null && level != null) {
                val stationPos = payload.stationPos
                val stationBe = level.getBlockEntity(stationPos) as? CameraStationBlockEntity
                if (stationBe == null || stationBe.ownerUuid != player.uuid.toString()) {
                    player.displayClientMessage(Component.literal("Error: You do not own this station!"), true)
                    return@enqueueWork
                }
                val cameraPos = payload.cameraPos
                if (!stationBe.linkedCameras.contains(cameraPos)) {
                    player.displayClientMessage(Component.literal("Error: Camera is not linked to this station!"), true)
                    return@enqueueWork
                }

                // Play chime sound at camera location
                // Draw a particle line from the station console to the camera block
                val start = Vec3.atCenterOf(stationPos)
                val end = Vec3.atCenterOf(cameraPos)
                val delta = end.subtract(start)
                val distance = start.distanceTo(end)
                val steps = (distance * 2.5).toInt().coerceAtLeast(8) // a particle every 0.4 blocks
                for (i in 0..steps) {
                    val ratio = i.toDouble() / steps
                    val px = start.x + delta.x * ratio
                    val py = start.y + delta.y * ratio
                    val pz = start.z + delta.z * ratio
                    level.sendParticles(
                        ParticleTypes.END_ROD,
                        px, py, pz,
                        1,
                        0.0, 0.0, 0.0,
                        0.0
                    )
                }
            }
        }
    }

    fun handleRenameCamera(payload: RenameCameraPayload, context: IPayloadContext) {
        // Reserved for future camera rename implementation
    }
}
