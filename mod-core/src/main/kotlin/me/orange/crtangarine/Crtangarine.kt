package me.orange.crtangarine

import me.orange.crtangarine.block.ModBlocks
import me.orange.crtangarine.block.CameraBlockRenderer
import me.orange.crtangarine.client.ClientInputHandler
import me.orange.crtangarine.client.onRegisterScreens
import me.orange.crtangarine.model.ModelLayers
import me.orange.crtangarine.network.ModConfiguration
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.model.ModelResourceLocation
import net.minecraft.resources.ResourceLocation
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.client.event.EntityRenderersEvent
import net.neoforged.neoforge.client.event.ModelEvent

import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.fml.event.lifecycle.FMLDedicatedServerSetupEvent
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import me.orange.crtangarine.network.ModNetworking
import net.neoforged.fml.ModLoadingContext
import net.neoforged.fml.config.ModConfig
import net.neoforged.neoforge.common.NeoForge.EVENT_BUS
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS
import thedarkcolour.kotlinforforge.neoforge.forge.runForDist


/**
 * Main mod class.
 *
 * An example for blocks is in the `blocks` package of this mod.
 */
import net.neoforged.bus.api.IEventBus

object Crtangarine {
    const val ID = "crtangarine"

    // the logger for our mod
    val LOGGER: Logger = LogManager.getLogger(ID)

    fun init(modBus: IEventBus) {
        LOGGER.info("Initializing CRT-angarine core...")

        // Register the KDeferredRegister to the mod-specific event bus
        ModBlocks.REGISTRY.register(modBus)
        ModBlocks.ITEM_REGISTRY.register(modBus)
        ModBlocks.BLOCK_ENTITY_REGISTRY.register(modBus)
        ModBlocks.MENU_REGISTRY.register(modBus)
        ModBlocks.CREATIVE_TAB_REGISTRY.register(modBus)

        // Register packet payload handlers
        modBus.addListener(ModNetworking::register)

        // Register camera locking tick events
        EVENT_BUS.register(me.orange.crtangarine.aim.CameraAimEvents)
        EVENT_BUS.register(me.orange.crtangarine.network.CameraServerEvents)
        EVENT_BUS.register(me.orange.crtangarine.aim.CameraStreamTask)

        // Register the common setup event listener directly on the mod bus
        modBus.addListener(::onCommonSetup)

        runForDist(
            clientTarget = {
                modBus.addListener(::onClientSetup)
                modBus.addListener(::onRegisterScreens)
                modBus.addListener(::onRegisterRenderers)
                modBus.addListener(::onRegisterAdditionalModels)
                EVENT_BUS.register(ClientInputHandler)
                Minecraft.getInstance()
            },
            serverTarget = {
                modBus.addListener(::onServerSetup)
                "server"
            })
    }

    /**
     * This is used for initializing client-specific
     * things such as renderers and keymaps
     * Fired on the mod-specific event bus.
     */
    private fun onClientSetup(event: FMLClientSetupEvent) {
        LOGGER.info("CRT-angarine client setup complete.")
    }

    private fun onRegisterRenderers(event: EntityRenderersEvent.RegisterRenderers) {
        event.registerBlockEntityRenderer(ModBlocks.CAMERA_BLOCK_ENTITY_TYPE, ::CameraBlockRenderer)
    }

    private fun onRegisterAdditionalModels(event: ModelEvent.RegisterAdditional) {
        event.register(
            ModelResourceLocation(
                ResourceLocation.fromNamespaceAndPath(ID, "block/security_camera_baseplate"),
                "standalone"
            )
        )
        event.register(
            ModelResourceLocation(
                ResourceLocation.fromNamespaceAndPath(ID, "block/security_camera_body"),
                "standalone"
            )
        )
    }

    /**
     * Fired on the global Forge bus.
     */
    private fun onServerSetup(event: FMLDedicatedServerSetupEvent) {
        LOGGER.info("CRT-angarine dedicated server setup complete.")
    }

    private fun onCommonSetup(event: FMLCommonSetupEvent) {
        LOGGER.info("CRT-angarine common setup complete.")
    }
}

