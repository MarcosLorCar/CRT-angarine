package me.orange.crtangarine.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.network.chat.Component
import net.minecraft.client.gui.GuiGraphics

import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent
import me.orange.crtangarine.block.ModBlocks

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

        val copyButton = Button.builder(Component.literal("Copy to Clipboard")) { button ->
            minecraft?.keyboardHandler?.clipboard = token
            minecraft?.player?.displayClientMessage(Component.literal("Token copied to clipboard!"), true)
            onClose()
        }.bounds(width / 2 - 80, height / 2 + 10, 160, 20).build()
        addRenderableWidget(copyButton)
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick)
        super.render(guiGraphics, mouseX, mouseY, partialTick)
    }
}
