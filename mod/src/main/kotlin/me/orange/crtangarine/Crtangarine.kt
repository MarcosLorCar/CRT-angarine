package me.orange.crtangarine

import me.orange.crtangarine.block.ModBlocks
import me.orange.crtangarine.client.ClientInputHandler
import me.orange.crtangarine.client.onRegisterScreens
import net.minecraft.client.Minecraft
import net.neoforged.fml.common.Mod

import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.fml.event.lifecycle.FMLDedicatedServerSetupEvent
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import me.orange.crtangarine.network.ModNetworking
import net.neoforged.neoforge.common.NeoForge.EVENT_BUS
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS
import thedarkcolour.kotlinforforge.neoforge.forge.runForDist


/**
 * Main mod class.
 *
 * An example for blocks is in the `blocks` package of this mod.
 */
@Mod(Crtangarine.ID)
object Crtangarine {
    const val ID = "crtangarine"

    // the logger for our mod
    val LOGGER: Logger = LogManager.getLogger(ID)

    init {
        LOGGER.log(Level.INFO, "Hello world!")

        // Register the KDeferredRegister to the mod-specific event bus
        ModBlocks.REGISTRY.register(MOD_BUS)
        ModBlocks.ITEM_REGISTRY.register(MOD_BUS)
        ModBlocks.BLOCK_ENTITY_REGISTRY.register(MOD_BUS)
        ModBlocks.MENU_REGISTRY.register(MOD_BUS)
        ModBlocks.CREATIVE_TAB_REGISTRY.register(MOD_BUS)

        // Register packet payload handlers
        MOD_BUS.addListener(ModNetworking::register)

        // Register camera locking tick events
        EVENT_BUS.register(me.orange.crtangarine.aim.CameraAimEvents)
        EVENT_BUS.register(me.orange.crtangarine.network.CameraServerEvents)
        EVENT_BUS.register(me.orange.crtangarine.aim.CameraStreamTask)

        // Register the common setup event listener directly on the mod bus
        MOD_BUS.addListener(::onCommonSetup)

        val obj = runForDist(
            clientTarget = {
                MOD_BUS.addListener(::onClientSetup)
                MOD_BUS.addListener(::onRegisterScreens)
                EVENT_BUS.register(ClientInputHandler)
                Minecraft.getInstance()
            },
            serverTarget = {
                MOD_BUS.addListener(::onServerSetup)
                "test"
            })


        println(obj)
    }

    /**
     * This is used for initializing client-specific
     * things such as renderers and keymaps
     * Fired on the mod-specific event bus.
     */
    private fun onClientSetup(event: FMLClientSetupEvent) {
        LOGGER.log(Level.INFO, "Initializing client...")
    }

    /**
     * Fired on the global Forge bus.
     */
    private fun onServerSetup(event: FMLDedicatedServerSetupEvent) {
        LOGGER.log(Level.INFO, "Server starting...")
    }

    private fun onCommonSetup(event: FMLCommonSetupEvent) {
        LOGGER.log(Level.INFO, "Hello! This is working!")
    }
}

