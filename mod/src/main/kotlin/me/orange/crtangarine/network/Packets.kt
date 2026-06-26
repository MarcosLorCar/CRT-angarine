package me.orange.crtangarine.network

import me.orange.crtangarine.Crtangarine
import me.orange.crtangarine.block.CameraStationBlockEntity
import me.orange.crtangarine.block.CameraBlockEntity
import me.orange.crtangarine.aim.CameraAimManager
import me.orange.crtangarine.aim.AimSession
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.PacketDistributor
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component
import thedarkcolour.kotlinforforge.neoforge.forge.runForDist

data class UpdateStationNamePayload(val pos: BlockPos, val name: String) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<UpdateStationNamePayload> = TYPE
    companion object {
        val TYPE = CustomPacketPayload.Type<UpdateStationNamePayload>(ResourceLocation.fromNamespaceAndPath(Crtangarine.ID, "update_station_name"))
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, UpdateStationNamePayload> = StreamCodec.of(
            { buf, value ->
                BlockPos.STREAM_CODEC.encode(buf, value.pos)
                buf.writeUtf(value.name)
            },
            { buf ->
                val pos = BlockPos.STREAM_CODEC.decode(buf)
                val name = buf.readUtf()
                UpdateStationNamePayload(pos, name)
            }
        )
    }
}

data class AimCameraPayload(val stationPos: BlockPos, val cameraPos: BlockPos) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<AimCameraPayload> = TYPE
    companion object {
        val TYPE = CustomPacketPayload.Type<AimCameraPayload>(ResourceLocation.fromNamespaceAndPath(Crtangarine.ID, "aim_camera"))
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, AimCameraPayload> = StreamCodec.of(
            { buf, value ->
                BlockPos.STREAM_CODEC.encode(buf, value.stationPos)
                BlockPos.STREAM_CODEC.encode(buf, value.cameraPos)
            },
            { buf ->
                val sPos = BlockPos.STREAM_CODEC.decode(buf)
                val cPos = BlockPos.STREAM_CODEC.decode(buf)
                AimCameraPayload(sPos, cPos)
            }
        )
    }
}

data class StartAimModePayload(val pos: BlockPos) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<StartAimModePayload> = TYPE
    companion object {
        val TYPE = CustomPacketPayload.Type<StartAimModePayload>(ResourceLocation.fromNamespaceAndPath(Crtangarine.ID, "start_aim_mode"))
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, StartAimModePayload> = StreamCodec.of(
            { buf, value -> BlockPos.STREAM_CODEC.encode(buf, value.pos) },
            { buf -> StartAimModePayload(BlockPos.STREAM_CODEC.decode(buf)) }
        )
    }
}

data class CommitCameraAimPayload(val cameraPos: BlockPos, val pitch: Float, val yaw: Float) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<CommitCameraAimPayload> = TYPE
    companion object {
        val TYPE = CustomPacketPayload.Type<CommitCameraAimPayload>(ResourceLocation.fromNamespaceAndPath(Crtangarine.ID, "commit_camera_aim"))
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, CommitCameraAimPayload> = StreamCodec.of(
            { buf, value ->
                BlockPos.STREAM_CODEC.encode(buf, value.cameraPos)
                buf.writeFloat(value.pitch)
                buf.writeFloat(value.yaw)
            },
            { buf ->
                val pos = BlockPos.STREAM_CODEC.decode(buf)
                val pitch = buf.readFloat()
                val yaw = buf.readFloat()
                CommitCameraAimPayload(pos, pitch, yaw)
            }
        )
    }
}

data class UnlinkCameraPayload(val stationPos: BlockPos, val cameraPos: BlockPos) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<UnlinkCameraPayload> = TYPE
    companion object {
        val TYPE = CustomPacketPayload.Type<UnlinkCameraPayload>(ResourceLocation.fromNamespaceAndPath(Crtangarine.ID, "unlink_camera"))
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, UnlinkCameraPayload> = StreamCodec.of(
            { buf, value ->
                BlockPos.STREAM_CODEC.encode(buf, value.stationPos)
                BlockPos.STREAM_CODEC.encode(buf, value.cameraPos)
            },
            { buf ->
                val sPos = BlockPos.STREAM_CODEC.decode(buf)
                val cPos = BlockPos.STREAM_CODEC.decode(buf)
                UnlinkCameraPayload(sPos, cPos)
            }
        )
    }
}

data class LocateCameraPayload(val stationPos: BlockPos, val cameraPos: BlockPos) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<LocateCameraPayload> = TYPE
    companion object {
        val TYPE = CustomPacketPayload.Type<LocateCameraPayload>(ResourceLocation.fromNamespaceAndPath(Crtangarine.ID, "locate_camera"))
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, LocateCameraPayload> = StreamCodec.of(
            { buf, value ->
                BlockPos.STREAM_CODEC.encode(buf, value.stationPos)
                BlockPos.STREAM_CODEC.encode(buf, value.cameraPos)
            },
            { buf ->
                val sPos = BlockPos.STREAM_CODEC.decode(buf)
                val cPos = BlockPos.STREAM_CODEC.decode(buf)
                LocateCameraPayload(sPos, cPos)
            }
        )
    }
}

data class RenameCameraPayload(val stationPos: BlockPos, val cameraPos: BlockPos, val newName: String) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<RenameCameraPayload> = TYPE
    companion object {
        val TYPE = CustomPacketPayload.Type<RenameCameraPayload>(ResourceLocation.fromNamespaceAndPath(Crtangarine.ID, "rename_camera"))
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, RenameCameraPayload> = StreamCodec.of(
            { buf, value ->
                BlockPos.STREAM_CODEC.encode(buf, value.stationPos)
                BlockPos.STREAM_CODEC.encode(buf, value.cameraPos)
                buf.writeUtf(value.newName)
            },
            { buf ->
                val sPos = BlockPos.STREAM_CODEC.decode(buf)
                val cPos = BlockPos.STREAM_CODEC.decode(buf)
                val name = buf.readUtf()
                RenameCameraPayload(sPos, cPos, name)
            }
        )
    }
}

object ModNetworking {
    fun register(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar(Crtangarine.ID)

        // Unlink Camera
        registrar.playToServer(
            UnlinkCameraPayload.TYPE,
            UnlinkCameraPayload.STREAM_CODEC
        ) { payload, context ->
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
                            me.orange.crtangarine.block.CameraStationRegistry.triggerUpdate()
                        }
                    }
                }
            }
        }

        // Update Station Name
        registrar.playToServer(
            UpdateStationNamePayload.TYPE,
            UpdateStationNamePayload.STREAM_CODEC
        ) { payload, context ->
            context.enqueueWork {
                val player = context.player()
                val level = player.level()
                val pos = payload.pos
                val blockEntity = level.getBlockEntity(pos) as? CameraStationBlockEntity
                if (blockEntity != null) {
                    if (blockEntity.ownerUuid.isEmpty() || blockEntity.ownerUuid == player.uuid.toString()) {
                        blockEntity.customName = payload.name
                        blockEntity.setChanged()
                        level.sendBlockUpdated(pos, blockEntity.blockState, blockEntity.blockState, 3)
                        me.orange.crtangarine.block.CameraStationRegistry.triggerUpdate()
                    }
                }
            }
        }

        // Aim Camera
        registrar.playToServer(
            AimCameraPayload.TYPE,
            AimCameraPayload.STREAM_CODEC
        ) { payload, context ->
            context.enqueueWork {
                val player = context.player() as? ServerPlayer
                val level = player?.serverLevel()
                if (player != null && level != null) {
                    val pos = payload.cameraPos
                    val state = level.getBlockState(pos)
                    val facing = if (state.hasProperty(me.orange.crtangarine.block.CameraBlock.FACING)) {
                        state.getValue(me.orange.crtangarine.block.CameraBlock.FACING)
                    } else if (state.hasProperty(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING)) {
                        state.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING)
                    } else {
                        net.minecraft.core.Direction.NORTH
                    }

                    val cameraBe = level.getBlockEntity(pos) as? CameraBlockEntity
                    val yaw = cameraBe?.yaw ?: facing.toYRot()
                    val pitch = cameraBe?.pitch ?: 0.0f

                    val originalGameMode = player.gameMode.gameModeForPlayer
                    val originalPos = player.position()
                    val originalPitch = player.xRot
                    val originalYaw = player.yRot
                    val originalFlying = player.abilities.flying

                    // Set game mode to SPECTATOR to enable proper camera movement and hide player model desyncs
                    player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR)

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

        // Start Aim Mode
        registrar.playToClient(
            StartAimModePayload.TYPE,
            StartAimModePayload.STREAM_CODEC
        ) { payload, context ->
            context.enqueueWork {
                runForDist(
                    clientTarget = { {
                        me.orange.crtangarine.client.ClientInputHandler.startAiming(payload.pos)
                    } },
                    serverTarget = { { } }
                ).invoke()
            }
        }

        // Commit Camera Aim
        registrar.playToServer(
            CommitCameraAimPayload.TYPE,
            CommitCameraAimPayload.STREAM_CODEC
        ) { payload, context ->
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

        // Locate Camera
        registrar.playToServer(
            LocateCameraPayload.TYPE,
            LocateCameraPayload.STREAM_CODEC
        ) { payload, context ->
            context.enqueueWork {
                val player = context.player() as? ServerPlayer
                val level = player?.serverLevel()
                if (player != null && level != null) {
                    val stationPos = payload.stationPos
                    val cameraPos = payload.cameraPos

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
                            net.minecraft.core.particles.ParticleTypes.END_ROD,
                            px, py, pz,
                            1,
                            0.0, 0.0, 0.0,
                            0.0
                        )
                    }
                }
            }
        }
    }
}
