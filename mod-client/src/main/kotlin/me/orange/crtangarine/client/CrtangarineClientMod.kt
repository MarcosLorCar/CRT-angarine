package me.orange.crtangarine.client

import me.orange.crtangarine.Crtangarine
import me.orange.crtangarine.network.ModConfiguration
import net.neoforged.fml.ModLoadingContext
import net.neoforged.fml.common.Mod
import net.neoforged.fml.config.ModConfig
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS

@Mod(Crtangarine.ID)
object CrtangarineClientMod {
    init {
        Crtangarine.LOGGER.info("Starting CRT-angarine in CLIENT-only mode...")

        // Configure client mode and register client configs
        ModConfiguration.activeMode = ModConfiguration.ModMode.CLIENT
        val container = ModLoadingContext.get().activeContainer
        container.registerConfig(ModConfig.Type.COMMON, ModConfiguration.CLIENT_SPEC)

        // Initialize core mod logic
        Crtangarine.init(MOD_BUS)
    }
}
