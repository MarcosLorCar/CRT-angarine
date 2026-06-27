package me.orange

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.send
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import me.orange.crtangarine.shared.*

data class WebClientSubscription(val cameraId: String, val worldId: String)
data class WebRegistrySubscription(val playerUuid: String, val worldId: String)

object CameraStreamRegistry {
    // worldId -> (stationPos -> StationInfo)
    val stationsByWorld = ConcurrentHashMap<String, ConcurrentHashMap<String, StationInfo>>()

    // Set of active streaming cameras (worldId/cameraId -> count of web clients watching it)
    val activeStreamingCameras = ConcurrentHashMap<String, Int>()

    // Active mod sessions (session -> worldId)
    val modSessions = ConcurrentHashMap<DefaultWebSocketSession, String>()

    // Active web client sessions (session -> WebClientSubscription)
    val webClients = ConcurrentHashMap<DefaultWebSocketSession, WebClientSubscription>()

    // Active web client registry updates sessions (session -> WebRegistrySubscription)
    val webRegistrySessions = ConcurrentHashMap<DefaultWebSocketSession, WebRegistrySubscription>()

    private fun getStationsForWorld(worldId: String): ConcurrentHashMap<String, StationInfo> {
        return stationsByWorld.computeIfAbsent(worldId) { id ->
            val file = File("stations_$id.json")
            val map = ConcurrentHashMap<String, StationInfo>()
            try {
                if (file.exists()) {
                    val content = file.readText()
                    val list = Json.decodeFromString<List<StationInfo>>(content)
                    for (station in list) {
                        map[station.pos] = station
                    }
                }
            } catch (e: Exception) {
                System.err.println("Error loading stations_$id.json: ${e.message}")
            }
            map
        }
    }

    private fun saveWorld(worldId: String) {
        val map = stationsByWorld[worldId] ?: return
        val file = File("stations_$worldId.json")
        try {
            val list = map.values.toList()
            val content = Json.encodeToString(list)
            file.writeText(content)
        } catch (e: Exception) {
            System.err.println("Error saving stations_$worldId.json: ${e.message}")
        }
    }

    fun updateStations(newStations: List<StationInfo>, worldId: String) {
        val worldMap = getStationsForWorld(worldId)
        val ownersToClear = newStations.map { it.ownerUuid }.toSet()
        worldMap.values.removeIf { it.ownerUuid in ownersToClear }
        for (station in newStations) {
            worldMap[station.pos] = station
        }
        saveWorld(worldId)
    }

    suspend fun broadcastRegistryToWebClients(playerUuid: String, worldId: String) {
        val userCameras = getCamerasForPlayer(playerUuid, worldId)
        val jsonStr = Json.encodeToString(userCameras)
        for ((session, sub) in webRegistrySessions) {
            if (sub.playerUuid == playerUuid && sub.worldId == worldId) {
                try {
                    session.send(jsonStr)
                } catch (e: java.lang.Exception) {
                    webRegistrySessions.remove(session)
                }
            }
        }
    }

    fun getCamerasForPlayer(playerUuid: String, worldId: String): List<CameraData> {
        val worldMap = getStationsForWorld(worldId)
        return worldMap.values
            .filter { it.ownerUuid == playerUuid }
            .flatMap { station ->
                station.cameras.map { cam ->
                    CameraData(
                        id = cam.pos,
                        name = cam.name,
                        x = cam.x,
                        y = cam.y,
                        z = cam.z,
                        isOnline = cam.isOnline,
                        stationName = if (station.customName.isNotEmpty()) station.customName else "Station [${station.pos}]"
                    )
                }
            }
    }

    suspend fun sendCommandToMod(command: CameraStreamCommand, worldId: String) {
        val jsonStr = Json.encodeToString(command)
        for ((session, id) in modSessions) {
            if (id == worldId) {
                try {
                    session.send(jsonStr)
                } catch (e: Exception) {
                    modSessions.remove(session)
                }
            }
        }
    }

    suspend fun syncActiveCamerasToMod(session: DefaultWebSocketSession, worldId: String) {
        val prefix = "$worldId/"
        for (key in activeStreamingCameras.keys) {
            if (key.startsWith(prefix)) {
                val cameraId = key.substring(prefix.length)
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
    }

    suspend fun forwardToWebClients(cameraId: String, worldId: String, text: String) {
        for ((session, sub) in webClients) {
            if (sub.cameraId == cameraId && sub.worldId == worldId) {
                try {
                    session.send(text)
                } catch (e: Exception) {
                    webClients.remove(session)
                }
            }
        }
    }

    suspend fun startStreaming(cameraId: String, worldId: String) {
        val key = "$worldId/$cameraId"
        val count = activeStreamingCameras.compute(key) { _, current ->
            (current ?: 0) + 1
        }
        if (count == 1) {
            // Signal the mod to start streaming
            val command = CameraStreamCommand(
                cameraId = cameraId,
                isActive = true
            )
            sendCommandToMod(command, worldId)
        }
    }

    suspend fun stopStreaming(cameraId: String, worldId: String) {
        val key = "$worldId/$cameraId"
        val count = activeStreamingCameras.compute(key) { _, current ->
            val next = (current ?: 0) - 1
            if (next <= 0) null else next
        }
        if (count == null || count <= 0) {
            // Signal the mod to stop streaming
            val command = CameraStreamCommand(
                cameraId = cameraId,
                isActive = false
            )
            sendCommandToMod(command, worldId)
        }
    }
}
