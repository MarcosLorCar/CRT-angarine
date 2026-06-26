package me.orange

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.send
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import me.orange.crtangarine.shared.*

object CameraStreamRegistry {
    // Station position string -> StationInfo
    val stations = ConcurrentHashMap<String, StationInfo>()

    // Set of active streaming cameras (cameraId -> count of web clients watching it)
    val activeStreamingCameras = ConcurrentHashMap<String, Int>()

    // Active mod sessions
    val modSessions = CopyOnWriteArrayList<DefaultWebSocketSession>()

    // Active web client sessions (session -> cameraId being watched)
    val webClients = ConcurrentHashMap<DefaultWebSocketSession, String>()

    // Active web client registry updates sessions (session -> playerUuid)
    val webRegistrySessions = ConcurrentHashMap<DefaultWebSocketSession, String>()

    fun updateStations(newStations: List<StationInfo>) {
        val ownersToClear = newStations.map { it.ownerUuid }.toSet()
        stations.values.removeIf { it.ownerUuid in ownersToClear }
        for (station in newStations) {
            stations[station.pos] = station
        }
    }

    suspend fun broadcastRegistryToWebClients(playerUuid: String) {
        val userCameras = getCamerasForPlayer(playerUuid)
        val jsonStr = Json.encodeToString(userCameras)
        for ((session, uuid) in webRegistrySessions) {
            if (uuid == playerUuid) {
                try {
                    session.send(jsonStr)
                } catch (e: java.lang.Exception) {
                    webRegistrySessions.remove(session)
                }
            }
        }
    }

    fun getCamerasForPlayer(playerUuid: String): List<CameraData> {
        return stations.values
            .filter { it.ownerUuid == playerUuid }
            .flatMap { station ->
                station.cameras.map { cam ->
                    CameraData(
                        id = cam.pos,
                        name = if (station.customName.isNotEmpty()) "${station.customName} - ${cam.name}" else cam.name,
                        x = cam.x,
                        y = cam.y,
                        z = cam.z,
                        isOnline = cam.isOnline
                    )
                }
            }
    }

    suspend fun sendCommandToMod(command: CameraStreamCommand) {
        val jsonStr = Json.encodeToString(command)
        for (session in modSessions) {
            try {
                session.send(jsonStr)
            } catch (e: Exception) {
                modSessions.remove(session)
            }
        }
    }

    suspend fun syncActiveCamerasToMod(session: DefaultWebSocketSession) {
        for (cameraId in activeStreamingCameras.keys) {
            val command = CameraStreamCommand(
                cameraId = cameraId,
                isActive = true
            )
            val jsonStr = Json.encodeToString(command)
            try {
                session.send(jsonStr)
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    suspend fun forwardToWebClients(cameraId: String, text: String) {
        for ((session, id) in webClients) {
            if (id == cameraId) {
                try {
                    session.send(text)
                } catch (e: Exception) {
                    webClients.remove(session)
                }
            }
        }
    }

    suspend fun startStreaming(cameraId: String) {
        val count = activeStreamingCameras.compute(cameraId) { _, current ->
            (current ?: 0) + 1
        }
        if (count == 1) {
            // Signal the mod to start streaming
            val command = CameraStreamCommand(
                cameraId = cameraId,
                isActive = true
            )
            sendCommandToMod(command)
        }
    }

    suspend fun stopStreaming(cameraId: String) {
        val count = activeStreamingCameras.compute(cameraId) { _, current ->
            val next = (current ?: 0) - 1
            if (next <= 0) null else next
        }
        if (count == null || count <= 0) {
            // Signal the mod to stop streaming
            val command = CameraStreamCommand(
                cameraId = cameraId,
                isActive = false
            )
            sendCommandToMod(command)
        }
    }
}
