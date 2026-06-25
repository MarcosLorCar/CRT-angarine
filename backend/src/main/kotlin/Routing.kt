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
import me.orange.crtangarine.shared.CameraData

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
                call.respond(LoginResponse(success = true, message = "Token acknowledged", token = request.token))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid request payload"))
            }
        }
        
        get("/api/cameras") {
            val mockCameras = listOf(
                CameraData(id = "cam-1", name = "Front Gate", x = 120.5, y = 64.0, z = -45.2, isOnline = true),
                CameraData(id = "cam-2", name = "Backyard Pool", x = 145.0, y = 63.0, z = -10.8, isOnline = false),
                CameraData(id = "cam-3", name = "Main Lobby", x = 100.2, y = 70.0, z = 5.0, isOnline = true)
            )
            call.respond(mockCameras)
        }

        webSocket("/ws") { // websocketSession
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    outgoing.send(Frame.Text("YOU SAID: $text"))
                    if (text.equals("bye", ignoreCase = true)) {
                        close(CloseReason(CloseReason.Codes.NORMAL, "Client said BYE"))
                    }
                }
            }
        }
        
        get("/json/kotlinx-serialization") {
            call.respond(mapOf("hello" to "world"))
        }
    }
}