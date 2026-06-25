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
import me.orange.crtangarine.shared.CameraData
import me.orange.crtangarine.shared.AuthTokenPacket


@Serializable
data class LoginRequest(val token: String)

@Serializable
data class LoginResponse(val success: Boolean, val message: String, val token: String)

fun Application.configureRouting() {
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
                application.environment.log.info("Registering token for player ${packet.playerUuid}")
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
            if (playerUuid == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Player not found for token"))
                return@get
            }
            val userCameras = CameraStreamRegistry.getCamerasForPlayer(playerUuid)
            call.respond(userCameras)
        }

        webSocket("/api/mod/stream") {
            application.environment.log.info("Mod connected to stream endpoint")
            CameraStreamRegistry.modSessions.add(this)
            try {
                CameraStreamRegistry.syncActiveCamerasToMod(this)
            } catch (e: Exception) {
                application.environment.log.error("Error syncing active cameras on mod connection: ${e.message}")
            }
            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        try {
                            val msg = Json.decodeFromString<me.orange.crtangarine.shared.ModMessage>(text)
                            when (msg) {
                                is me.orange.crtangarine.shared.RegistryUpdateMessage -> {
                                    CameraStreamRegistry.updateStations(msg.data.stations)
                                }
                                is me.orange.crtangarine.shared.FrustumPayloadMessage -> {
                                    CameraStreamRegistry.forwardToWebClients(msg.data.cameraId, text)
                                }
                                is me.orange.crtangarine.shared.EntityStreamMessage -> {
                                    CameraStreamRegistry.forwardToWebClients(msg.data.cameraId, text)
                                }
                            }
                        } catch (e: Exception) {
                            application.environment.log.error("Error decoding message from mod: ${e.message}")
                        }
                    }
                }
            } finally {
                CameraStreamRegistry.modSessions.remove(this)
                application.environment.log.info("Mod disconnected from stream endpoint")
            }
        }

        webSocket("/api/webapp/view") {
            val token = call.request.queryParameters["token"]
            val cameraId = call.request.queryParameters["cameraId"]
            if (token == null || cameraId == null || !TokenRegistry.validateToken(token)) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized or missing parameters"))
                return@webSocket
            }

            application.environment.log.info("Web client subscribed to camera $cameraId")
            CameraStreamRegistry.webClients[this] = cameraId
            CameraStreamRegistry.startStreaming(cameraId)

            try {
                for (frame in incoming) {
                    // Keep connection open
                }
            } finally {
                CameraStreamRegistry.webClients.remove(this)
                CameraStreamRegistry.stopStreaming(cameraId)
                application.environment.log.info("Web client unsubscribed from camera $cameraId")
            }
        }
    }
}