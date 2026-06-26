package me.orange.crtangarine.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.network.chat.Component
import net.minecraft.client.gui.GuiGraphics

import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent
import me.orange.crtangarine.block.ModBlocks
import me.orange.crtangarine.network.ModConfiguration

fun openKeycardScreen(token: String) {
    Minecraft.getInstance().setScreen(KeycardScreen(token))
}

fun onRegisterScreens(event: RegisterMenuScreensEvent) {
    event.register(ModBlocks.CAMERA_STATION_MENU_TYPE, ::CameraStationScreen)
}


class KeycardScreen(private val token: String) : Screen(Component.literal("Security Keycard")) {
    override fun init() {
        super.init()

        val titleWidget = StringWidget(width / 2 - 100, height / 2 - 40, 200, 20, Component.literal("Your Security Token:"), font)
        addRenderableWidget(titleWidget)

        val tokenWidget = StringWidget(width / 2 - 100, height / 2 - 20, 200, 20, Component.literal(token), font)
        addRenderableWidget(tokenWidget)

        // Copy button on the left
        val copyButton = Button.builder(Component.literal("Copy Token")) { button ->
            minecraft?.keyboardHandler?.clipboard = token
            minecraft?.player?.displayClientMessage(Component.literal("Token copied to clipboard!"), true)
            onClose()
        }.bounds(width / 2 - 115, height / 2 + 10, 110, 20).build()
        addRenderableWidget(copyButton)

        // Open Link button on the right
        val openLinkButton = Button.builder(Component.literal("Open Link")) { button ->
            val backendUri = ModConfiguration.CONFIG.backendUri.get()
            val urlString = if (backendUri.startsWith("http://") || backendUri.startsWith("https://")) {
                "$backendUri/?token=$token"
            } else {
                "http://$backendUri/?token=$token"
            }
            try {
                net.minecraft.Util.getPlatform().openUri(java.net.URI(urlString))
            } catch (e: Exception) {
                minecraft?.player?.displayClientMessage(Component.literal("Could not open link: ${e.message}"), true)
            }
            onClose()
        }.bounds(width / 2 + 5, height / 2 + 10, 110, 20).build()
        addRenderableWidget(openLinkButton)
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick)
        super.render(guiGraphics, mouseX, mouseY, partialTick)
    }
}
