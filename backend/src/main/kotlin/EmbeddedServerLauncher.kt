package me.orange

import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.connector

object EmbeddedServerLauncher {
    private var server: EmbeddedServer<*, *>? = null

    @JvmStatic
    fun start(port: Int) {
        if (server != null) return
        
        // Spin up Ktor CIO server on the specified port using the classloader that loaded this class
        server = embeddedServer(
            CIO,
            environment = applicationEnvironment {
                classLoader = EmbeddedServerLauncher::class.java.classLoader
            },
            configure = {
                connector {
                    this.port = port
                }
            }
        ) {
            configureSerialization()
            configureWebsockets()
            configureRouting()
        }.apply {
            start(wait = false)
        }
    }

    @JvmStatic
    fun stop() {
        server?.let {
            it.stop(1000, 5000)
            server = null
        }
    }
}
