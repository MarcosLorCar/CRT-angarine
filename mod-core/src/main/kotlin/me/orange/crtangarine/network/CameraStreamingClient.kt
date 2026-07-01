package me.orange.crtangarine.network

import io.ktor.client.*
import io.ktor.client.engine.java.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import me.orange.crtangarine.Crtangarine
import me.orange.crtangarine.block.CameraStationRegistry
import me.orange.crtangarine.shared.*
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import me.orange.crtangarine.world.StationRegistrySavedData
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

object CameraStreamingClient {
    private val client = HttpClient(Java) {
        install(WebSockets)
    }

    private var session: DefaultClientWebSocketSession? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Camera block position -> isActive
    val activeStreamingCameras = ConcurrentHashMap<BlockPos, Boolean>()

    @Volatile
    private var minecraftServer: net.minecraft.server.MinecraftServer? = null

    fun start(server: net.minecraft.server.MinecraftServer) {
        this.minecraftServer = server
        scope.launch {
            while (isActive) {
                try {
                    Crtangarine.LOGGER.info("Connecting to Ktor server stream endpoint...")
                    val backendUri = ModConfiguration.getEffectiveBackendUri()
                    val worldId = if (minecraftServer != null) {
                        me.orange.crtangarine.world.WorldIdSavedData.get(minecraftServer!!.overworld()).worldId
                    } else {
                        "global"
                    }
                    client.webSocket("ws://$backendUri/api/mod/stream?worldId=$worldId") {
                        session = this
                        Crtangarine.LOGGER.info("Connected to Ktor server stream endpoint successfully.")

                        // Send initial registry state
                        sendRegistryUpdate()

                        // Listen for incoming commands from server
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val text = frame.readText()
                                try {
                                    val cmd = Json.decodeFromString<CameraStreamCommand>(text)
                                    handleStreamCommand(cmd)
                                } catch (e: Exception) {
                                    Crtangarine.LOGGER.error("Failed to decode command: $text", e)
                                }
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    Crtangarine.LOGGER.error("WebSocket connection error: ${e.message}. Retrying in 5 seconds...")
                } finally {
                    session = null
                }
                delay(5000)
            }
        }
    }

    fun stop() {
        scope.cancel()
        client.close()
        this.minecraftServer = null
    }

    private fun handleStreamCommand(cmd: CameraStreamCommand) {
        val parts = cmd.cameraId.split(",")
        if (parts.size == 3) {
            try {
                val pos = BlockPos(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
                if (cmd.isActive) {
                    activeStreamingCameras[pos] = true
                    Crtangarine.LOGGER.info("Camera stream activated: $pos")
                } else {
                    activeStreamingCameras.remove(pos)
                    Crtangarine.LOGGER.info("Camera stream deactivated: $pos")
                }
            } catch (e: Exception) {
                Crtangarine.LOGGER.error("Error parsing camera ID block pos: ${cmd.cameraId}", e)
            }
        }
    }

    fun sendRegistryUpdate() {
        val currentSession = session ?: return
        val server = minecraftServer
        if (server == null) {
            Crtangarine.LOGGER.warn("Cannot send registry update: MinecraftServer is not set.")
            return
        }

        scope.launch {
            try {
                // Query block entities and saved data safely on the server main thread
                val stationInfosFuture = java.util.concurrent.CompletableFuture.supplyAsync(
                    java.util.function.Supplier {
                        val savedData = StationRegistrySavedData.get(server.overworld())
                        savedData.getStations().map { entry ->
                            val level = server.getLevel(entry.dimension) ?: server.overworld()
                            val isStationLoaded = level.hasChunk(entry.pos.x shr 4, entry.pos.z shr 4)
                            val stationBe = if (isStationLoaded) {
                                level.getBlockEntity(entry.pos) as? me.orange.crtangarine.block.CameraStationBlockEntity
                            } else null

                            val customName = stationBe?.customName ?: entry.customName
                            val linkedCameras = stationBe?.linkedCameras ?: entry.linkedCameras

                            val cameraInfos = linkedCameras.map { camPos ->
                                val isCameraLoaded = level.hasChunk(camPos.x shr 4, camPos.z shr 4)
                                val cameraBe = if (isCameraLoaded) {
                                    level.getBlockEntity(camPos) as? me.orange.crtangarine.block.CameraBlockEntity
                                } else null

                                val status = when {
                                    isCameraLoaded && cameraBe != null && cameraBe.linkedStationPos == entry.pos -> "ONLINE"
                                    isCameraLoaded -> "OFFLINE"
                                    else -> "UNLOADED"
                                }
                                val isOnline = status == "ONLINE"

                                CameraInfo(
                                    pos = "${camPos.x},${camPos.y},${camPos.z}",
                                    name = "Camera (${camPos.x}, ${camPos.y}, ${camPos.z})",
                                    x = camPos.x + 0.5,
                                    y = camPos.y + 0.5,
                                    z = camPos.z + 0.5,
                                    isOnline = isOnline,
                                    status = status
                                )
                            }

                            StationInfo(
                                ownerUuid = entry.ownerUuid,
                                customName = customName.ifEmpty { "Camera Station (${entry.pos.x}, ${entry.pos.y}, ${entry.pos.z})" },
                                pos = "${entry.pos.x},${entry.pos.y},${entry.pos.z}",
                                cameras = cameraInfos
                            )
                        }
                    },
                    server
                )

                val stationInfos = stationInfosFuture.get()

                val update = CameraRegistryUpdate(stations = stationInfos)
                val msg = RegistryUpdateMessage(update)
                val jsonStr = Json.encodeToString<ModMessage>(msg)
                currentSession.send(jsonStr)
                Crtangarine.LOGGER.info("Sent camera registry update to Ktor server.")
            } catch (e: Exception) {
                Crtangarine.LOGGER.error("Failed to send registry update: ${e.message}", e)
            }
        }
    }

    fun sendFrustumPayload(cameraId: String, pitch: Float, yaw: Float, blocks: List<BlockData>) {
        val currentSession = session ?: return
        scope.launch {
            try {
                val payload = TerrainFrustumPayload(
                    cameraId = cameraId,
                    pitch = pitch,
                    yaw = yaw,
                    blocks = blocks
                )
                val msg = FrustumPayloadMessage(payload)
                val jsonStr = Json.encodeToString<ModMessage>(msg)
                currentSession.send(jsonStr)
            } catch (e: Exception) {
                Crtangarine.LOGGER.error("Failed to send frustum payload: ${e.message}")
            }
        }
    }

    fun sendEntityDeltaStream(cameraId: String, entities: List<EntityData>) {
        val currentSession = session ?: return
        scope.launch {
            try {
                val payload = EntityDeltaStream(
                    cameraId = cameraId,
                    entities = entities
                )
                val msg = EntityStreamMessage(payload)
                val jsonStr = Json.encodeToString<ModMessage>(msg)
                currentSession.send(jsonStr)
            } catch (e: Exception) {
                Crtangarine.LOGGER.error("Failed to send entity stream: ${e.message}")
            }
        }
    }
}
