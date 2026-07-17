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
import com.google.gson.Gson
import com.google.gson.JsonParser
import io.ktor.server.application.ServerReady
import java.io.File
import me.orange.crtangarine.shared.*
import org.slf4j.LoggerFactory

@Serializable
data class LoginRequest(
    val token: String? = null,
    val username: String? = null,
    val password: String? = null
)

@Serializable
data class LoginResponse(val success: Boolean, val message: String, val token: String)

fun Application.configureRouting() {
    val logger = LoggerFactory.getLogger("me.orange.Server")
    val gson = Gson()
    monitor.subscribe(ServerReady) { env ->
        val port = env.config.propertyOrNull("ktor.deployment.port")?.getString() ?: "8080"
        logger.info("CRT-angarine Webserver is ready! Responding at http://localhost:$port")
    }

    intercept(ApplicationCallPipeline.Plugins) {
        call.response.headers.append("Clear-Site-Data", "\"cache\"")
    }
    
    routing {
        val paths = listOf(
            File("webapp/dist"),
            File("../webapp/dist"),
            File("../../webapp/dist")
        )
        val staticDir = paths.firstOrNull { it.exists() && it.isDirectory }
        if (staticDir != null) {
            logger.info("Serving static frontend files from local filesystem: ${staticDir.absolutePath}")
            singlePageApplication {
                useResources = false
                filesPath = staticDir.absolutePath
                defaultPage = "index.html"
            }
        } else {
            logger.info("Serving static frontend files from packaged classpath resources (static/) via custom ClassLoader router")
            get("/{path...}") {
                val path = call.parameters.getAll("path")?.joinToString("/") ?: ""
                val resourcePath = if (path.isEmpty()) "static/index.html" else "static/$path"
                val classLoader = EmbeddedServerLauncher::class.java.classLoader
                val stream = classLoader.getResourceAsStream(resourcePath)
                if (stream != null) {
                    val contentType = when {
                        resourcePath.endsWith(".html") -> ContentType.Text.Html
                        resourcePath.endsWith(".css") -> ContentType.Text.CSS
                        resourcePath.endsWith(".js") -> ContentType.Application.JavaScript
                        resourcePath.endsWith(".svg") -> ContentType.Image.SVG
                        resourcePath.endsWith(".json") -> ContentType.Application.Json
                        resourcePath.endsWith(".ico") -> ContentType.Image.XIcon
                        else -> ContentType.Application.OctetStream
                    }
                    call.respondBytes(stream.readBytes(), contentType)
                } else {
                    val indexStream = classLoader.getResourceAsStream("static/index.html")
                    if (indexStream != null) {
                        call.respondBytes(indexStream.readBytes(), ContentType.Text.Html)
                    } else {
                        call.respond(HttpStatusCode.NotFound, "Not Found")
                    }
                }
            }
        }
        
        post("/api/login") {
            try {
                val request = call.receive<LoginRequest>()
                application.environment.log.info("Received login request: username=${request.username}, hasToken=${request.token != null}")
                
                var playerUuid: String? = null
                var worldId = "global"
                var sessionToken: String? = null
                var loginErrorMessage = "Invalid credentials."

                if (request.token != null && request.token.isNotEmpty()) {
                    val decrypted = me.orange.crtangarine.shared.CryptoUtils.decrypt(request.token)
                    val username = request.username
                    if (username != null && username.isNotEmpty()) {
                        if (decrypted.isNotEmpty()) {
                            val auth = TokenRegistry.validateCredentials(username, decrypted)
                            if (auth != null) {
                                playerUuid = auth.playerUuid
                                worldId = auth.worldId
                                sessionToken = TokenRegistry.createSession(username, playerUuid, worldId)
                            } else {
                                val exists = TokenRegistry.hasUser(username)
                                loginErrorMessage = if (!exists) {
                                    "Player name '$username' is not registered."
                                } else {
                                    "Incorrect password token."
                                }
                            }
                        } else {
                            loginErrorMessage = "Invalid or expired session token."
                        }
                    } else {
                        loginErrorMessage = "Username is required for token auto-login. Direct token login is disabled."
                    }
                } else if (request.username != null && request.password != null) {
                    val username = request.username
                    val password = request.password
                    if (username.isEmpty() || password.isEmpty()) {
                        loginErrorMessage = "Username and password are required."
                    } else {
                        val auth = TokenRegistry.validateCredentials(username, password)
                        if (auth != null) {
                            playerUuid = auth.playerUuid
                            worldId = auth.worldId
                            sessionToken = TokenRegistry.createSession(username, playerUuid, worldId)
                        } else {
                            val exists = TokenRegistry.hasUser(username)
                            loginErrorMessage = if (!exists) {
                                "Player name '$username' is not registered. Right-click your keycard in Minecraft first."
                            } else {
                                "Incorrect password."
                            }
                        }
                    }
                } else {
                    loginErrorMessage = "Please enter your username and password."
                }

                if (sessionToken != null) {
                    call.respond(LoginResponse(success = true, message = "Login successful", token = sessionToken))
                } else {
                    call.respond(HttpStatusCode.Unauthorized, LoginResponse(success = false, message = loginErrorMessage, token = ""))
                }
            } catch (e: Exception) {
                application.environment.log.error("Login processing error", e)
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
            call.respondText(gson.toJson(userCameras), ContentType.Application.Json)
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
                            val jsonObject = JsonParser.parseString(text).asJsonObject
                            val type = jsonObject.get("type")?.asString
                            
                            // Map the message types. We check both fully-qualified package names (sent by the mod)
                            // and shorthands. Fully-qualified names are forwarded downstream to preserve compatibility 
                            // with the Three.js viewport event handlers in the web client (Viewport.tsx).
                            val isRegistryUpdate = type == "me.orange.crtangarine.shared.RegistryUpdateMessage" || type == "registry_update"
                            val isFrustumPayload = type == "me.orange.crtangarine.shared.FrustumPayloadMessage" || type == "frustum_payload"
                            val isEntityStream = type == "me.orange.crtangarine.shared.EntityStreamMessage" || type == "entity_stream"
                            
                            if (isRegistryUpdate) {
                                val dataObj = jsonObject.getAsJsonObject("data")
                                val stationsArray = gson.fromJson(dataObj.getAsJsonArray("stations"), Array<StationInfo>::class.java)
                                val stations = stationsArray.toList()
                                CameraStreamRegistry.updateStations(stations, worldId)
                                for (station in stations) {
                                    CameraStreamRegistry.broadcastRegistryToWebClients(station.ownerUuid, worldId)
                                }
                            } else if (isFrustumPayload || isEntityStream) {
                                val dataObj = jsonObject.getAsJsonObject("data")
                                val cameraId = dataObj.get("cameraId")?.asString
                                if (cameraId != null) {
                                    CameraStreamRegistry.forwardToWebClients(cameraId, worldId, text)
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
             send(gson.toJson(userCameras))

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