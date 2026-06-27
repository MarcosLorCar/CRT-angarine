package me.orange.crtangarine.network

import me.orange.crtangarine.Crtangarine
import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

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

data class OpenKeycardScreenPayload(val token: String) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<OpenKeycardScreenPayload> = TYPE
    companion object {
        val TYPE = CustomPacketPayload.Type<OpenKeycardScreenPayload>(ResourceLocation.fromNamespaceAndPath(Crtangarine.ID, "open_keycard_screen"))
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, OpenKeycardScreenPayload> = StreamCodec.of(
            { buf, value -> buf.writeUtf(value.token) },
            { buf -> OpenKeycardScreenPayload(buf.readUtf()) }
        )
    }
}
