package me.orange

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import io.ktor.server.application.ServerReady
import me.orange.crtangarine.shared.*
import org.slf4j.LoggerFactory

@Serializable
data class LoginRequest(val token: String)

@Serializable
data class LoginResponse(val success: Boolean, val message: String, val token: String)

fun Application.configureRouting() {
    val logger = LoggerFactory.getLogger("me.orange.Server")
    monitor.subscribe(ServerReady) { env ->
        val port = env.config.propertyOrNull("ktor.deployment.port")?.getString() ?: "8080"
        logger.info("CRT-angarine Webserver is ready! Responding at http://localhost:$port")
    }

    routing {
        singlePageApplication {
            useResources = true
            filesPath = "static"
            defaultPage = "index.html"
        }
        
        post("/api/login") {
            try {
                val request = call.receive<LoginRequest>()
                application.environment.log.info("Received login request with token: ${request.token}")
                if (TokenRegistry.validateToken(request.token)) {
                    call.respond(LoginResponse(success = true, message = "Token acknowledged", token = request.token))
                } else {
                    call.respond(HttpStatusCode.Unauthorized, LoginResponse(success = false, message = "Invalid token", token = ""))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid request payload"))
            }
        }

        post("/api/register-token") {
            try {
                val packet = call.receive<AuthTokenPacket>()
                application.environment.log.info("Registering token for player ${packet.playerUuid} in world ${packet.worldId}")
                TokenRegistry.registerFromPacket(packet)
                call.respond(HttpStatusCode.OK, mapOf("status" to "registered"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid packet payload"))
            }
        }

        get("/api/cameras") {
            val token = call.request.headers["Authorization"] ?: call.request.queryParameters["token"]
            if (token == null || !TokenRegistry.validateToken(token)) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or missing token"))
                return@get
            }
            val playerUuid = TokenRegistry.getPlayerUuid(token)
            val worldId = TokenRegistry.getWorldId(token) ?: "global"
            if (playerUuid == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Player not found for token"))
                return@get
            }
            val userCameras = CameraStreamRegistry.getCamerasForPlayer(playerUuid, worldId)
            call.respondText(Json.encodeToString(userCameras), ContentType.Application.Json)
        }

        webSocket("/api/mod/stream") {
            val worldId = call.request.queryParameters["worldId"] ?: "global"
            application.environment.log.info("Mod connected to stream endpoint for world $worldId")
            CameraStreamRegistry.modSessions[this] = worldId
            try {
                CameraStreamRegistry.syncActiveCamerasToMod(this, worldId)
            } catch (e: Exception) {
                application.environment.log.error("Error syncing active cameras on mod connection: ${e.message}")
            }
            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        try {
                            val msg = Json.decodeFromString<ModMessage>(text)
                            when (msg) {
                                is RegistryUpdateMessage -> {
                                    CameraStreamRegistry.updateStations(msg.data.stations, worldId)
                                    for (station in msg.data.stations) {
                                        CameraStreamRegistry.broadcastRegistryToWebClients(station.ownerUuid, worldId)
                                    }
                                }
                                is FrustumPayloadMessage -> {
                                    CameraStreamRegistry.forwardToWebClients(msg.data.cameraId, worldId, text)
                                }
                                is EntityStreamMessage -> {
                                    CameraStreamRegistry.forwardToWebClients(msg.data.cameraId, worldId, text)
                                }
                            }
                        } catch (e: Exception) {
                            application.environment.log.error("Error decoding message from mod: ${e.message}")
                        }
                    }
                }
            } finally {
                CameraStreamRegistry.modSessions.remove(this)
                application.environment.log.info("Mod disconnected from stream endpoint for world $worldId")
            }
        }

        webSocket("/api/webapp/view") {
            val token = call.request.queryParameters["token"]
            val cameraId = call.request.queryParameters["cameraId"]
            if (token == null || cameraId == null || !TokenRegistry.validateToken(token)) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized or missing parameters"))
                return@webSocket
            }
            val worldId = TokenRegistry.getWorldId(token) ?: "global"

            application.environment.log.info("Web client subscribed to camera $cameraId in world $worldId")
            CameraStreamRegistry.webClients[this] = WebClientSubscription(cameraId, worldId)
            CameraStreamRegistry.startStreaming(cameraId, worldId)

            try {
                for (frame in incoming) {
                    // Keep connection open
                }
            } finally {
                CameraStreamRegistry.webClients.remove(this)
                CameraStreamRegistry.stopStreaming(cameraId, worldId)
                application.environment.log.info("Web client unsubscribed from camera $cameraId in world $worldId")
            }
        }

        webSocket("/api/webapp/registry") {
            val token = call.request.queryParameters["token"]
            if (token == null || !TokenRegistry.validateToken(token)) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized or missing parameters"))
                return@webSocket
            }

            val playerUuid = TokenRegistry.getPlayerUuid(token)
            val worldId = TokenRegistry.getWorldId(token) ?: "global"
            if (playerUuid == null) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Player not found"))
                return@webSocket
            }

            CameraStreamRegistry.webRegistrySessions[this] = WebRegistrySubscription(playerUuid, worldId)
            // Send initial camera list immediately upon connection
            val userCameras = CameraStreamRegistry.getCamerasForPlayer(playerUuid, worldId)
            send(Json.encodeToString(userCameras))

            try {
                for (frame in incoming) {
                    // Keep connection open
                }
            } finally {
                CameraStreamRegistry.webRegistrySessions.remove(this)
            }
        }
    }
}