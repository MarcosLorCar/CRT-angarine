package me.orange.crtangarine.network

import me.orange.crtangarine.Crtangarine
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent

object ModNetworking {
    fun register(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar(Crtangarine.ID)

        // Unlink Camera
        registrar.playToServer(
            UnlinkCameraPayload.TYPE,
            UnlinkCameraPayload.STREAM_CODEC,
            ServerPacketHandler::handleUnlinkCamera
        )

        // Update Station Name
        registrar.playToServer(
            UpdateStationNamePayload.TYPE,
            UpdateStationNamePayload.STREAM_CODEC,
            ServerPacketHandler::handleUpdateStationName
        )

        // Aim Camera
        registrar.playToServer(
            AimCameraPayload.TYPE,
            AimCameraPayload.STREAM_CODEC,
            ServerPacketHandler::handleAimCamera
        )

        // Start Aim Mode (Clientbound)
        registrar.playToClient(
            StartAimModePayload.TYPE,
            StartAimModePayload.STREAM_CODEC,
            ClientPacketHandler::handleStartAimMode
        )

        // Commit Camera Aim
        registrar.playToServer(
            CommitCameraAimPayload.TYPE,
            CommitCameraAimPayload.STREAM_CODEC,
            ServerPacketHandler::handleCommitCameraAim
        )

        // Locate Camera
        registrar.playToServer(
            LocateCameraPayload.TYPE,
            LocateCameraPayload.STREAM_CODEC,
            ServerPacketHandler::handleLocateCamera
        )

        // Rename Camera
        registrar.playToServer(
            RenameCameraPayload.TYPE,
            RenameCameraPayload.STREAM_CODEC,
            ServerPacketHandler::handleRenameCamera
        )

        // Open Keycard Screen (Clientbound)
        registrar.playToClient(
            OpenKeycardScreenPayload.TYPE,
            OpenKeycardScreenPayload.STREAM_CODEC,
            ClientPacketHandler::handleOpenKeycardScreen
        )
    }
}
