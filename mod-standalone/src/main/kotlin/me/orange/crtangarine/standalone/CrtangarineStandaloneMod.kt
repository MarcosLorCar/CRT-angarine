package me.orange.crtangarine.standalone

import me.orange.crtangarine.Crtangarine
import me.orange.crtangarine.network.ModConfiguration
import me.orange.EmbeddedServerLauncher
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.ModLoadingContext
import net.neoforged.fml.common.Mod
import net.neoforged.fml.config.ModConfig
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.server.ServerStartingEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS

@Mod(Crtangarine.ID)
object CrtangarineStandaloneMod {
    init {
        Crtangarine.LOGGER.info("Starting CRT-angarine in STANDALONE (embedded server) mode...")

        // Configure standalone mode and register standalone configs
        ModConfiguration.activeMode = ModConfiguration.ModMode.STANDALONE
        val container = ModLoadingContext.get().activeContainer
        container.registerConfig(ModConfig.Type.COMMON, ModConfiguration.STANDALONE_SPEC)

        // Initialize core mod logic
        Crtangarine.init(MOD_BUS)

        // Register standalone-specific server lifecycle events
        NeoForge.EVENT_BUS.register(StandaloneServerLauncher)
    }

    object StandaloneServerLauncher {
        @SubscribeEvent
        fun onServerStarting(event: ServerStartingEvent) {
            val port = ModConfiguration.STANDALONE_CONFIG.embeddedServerPort.get()
            Crtangarine.LOGGER.info("Starting embedded standalone Ktor server on port $port...")
            try {
                EmbeddedServerLauncher.start(port)
                Crtangarine.LOGGER.info("Embedded Ktor server started successfully.")
            } catch (e: Exception) {
                Crtangarine.LOGGER.error("Failed to start embedded Ktor server: ", e)
            }
        }

        @SubscribeEvent
        fun onServerStopping(event: ServerStoppingEvent) {
            Crtangarine.LOGGER.info("Stopping embedded standalone Ktor server...")
            try {
                EmbeddedServerLauncher.stop()
                Crtangarine.LOGGER.info("Embedded Ktor server stopped successfully.")
            } catch (e: Exception) {
                Crtangarine.LOGGER.error("Failed to stop embedded Ktor server: ", e)
            }
        }
    }
}
