package me.orange.crtangarine.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component
import net.minecraft.client.gui.GuiGraphics

import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent
import me.orange.crtangarine.block.ModBlocks
import me.orange.crtangarine.network.ModConfiguration
import me.orange.crtangarine.network.SetKeycardPasswordPayload

fun openKeycardScreen(token: String) {
    if (token.isEmpty()) {
        Minecraft.getInstance().setScreen(KeycardPasswordScreen())
    } else {
        Minecraft.getInstance().setScreen(KeycardScreen(token))
    }
}

fun onRegisterScreens(event: RegisterMenuScreensEvent) {
    event.register(ModBlocks.CAMERA_STATION_MENU_TYPE, ::CameraStationScreen)
}


class KeycardScreen(private val token: String) : Screen(Component.literal("Security Keycard")) {
    private var showPassword = false

    override fun init() {
        super.init()

        val username = minecraft?.player?.scoreboardName ?: "Unknown"
        val decryptedPassword = me.orange.crtangarine.shared.CryptoUtils.decrypt(token)

        val titleWidget = StringWidget(width / 2 - 100, height / 2 - 50, 200, 20, Component.literal("CRT Surveillance Hub").withStyle { it.withBold(true) }, font)
        addRenderableWidget(titleWidget)

        val playerWidget = StringWidget(width / 2 - 100, height / 2 - 30, 200, 20, Component.literal("Linked Player: $username"), font)
        addRenderableWidget(playerWidget)

        val statusWidget = StringWidget(width / 2 - 100, height / 2 - 10, 200, 20, Component.literal("Status: Password Configured"), font)
        addRenderableWidget(statusWidget)

        val passwordWidget = StringWidget(width / 2 - 100, height / 2 + 10, 200, 20, Component.literal("Password: ••••••••"), font)
        addRenderableWidget(passwordWidget)

        // Show/Hide toggle button
        val showButton = Button.builder(Component.literal("Show")) { button ->
            showPassword = !showPassword
            if (showPassword) {
                button.message = Component.literal("Hide")
                passwordWidget.message = Component.literal("Password: $decryptedPassword")
            } else {
                button.message = Component.literal("Show")
                passwordWidget.message = Component.literal("Password: ••••••••")
            }
        }.bounds(width / 2 - 135, height / 2 + 35, 60, 20).build()
        addRenderableWidget(showButton)

        // Change password button
        val changeButton = Button.builder(Component.literal("Change")) { button ->
            minecraft?.setScreen(KeycardPasswordScreen())
        }.bounds(width / 2 - 70, height / 2 + 35, 60, 20).build()
        addRenderableWidget(changeButton)

        // Open Dashboard button
        val openLinkButton = Button.builder(Component.literal("Open Site")) { button ->
            val backendUri = ModConfiguration.getEffectiveBackendUri()
            val urlString = if (backendUri.startsWith("http://") || backendUri.startsWith("https://")) {
                "$backendUri/?username=$username&token=$token"
            } else {
                "http://$backendUri/?username=$username&token=$token"
            }
            try {
                net.minecraft.Util.getPlatform().openUri(java.net.URI(urlString))
            } catch (e: Exception) {
                minecraft?.player?.displayClientMessage(Component.literal("Could not open link: ${e.message}"), true)
            }
        }.bounds(width / 2 - 5, height / 2 + 35, 80, 20).build()
        addRenderableWidget(openLinkButton)

        // Close button
        val closeButton = Button.builder(Component.literal("Close")) { button ->
            onClose()
        }.bounds(width / 2 + 80, height / 2 + 35, 55, 20).build()
        addRenderableWidget(closeButton)
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick)
        super.render(guiGraphics, mouseX, mouseY, partialTick)
    }
}

class KeycardPasswordScreen : Screen(Component.literal("Configure Keycard Password")) {
    private var passwordEditBox: EditBox? = null

    override fun init() {
        super.init()

        val labelWidget = StringWidget(width / 2 - 100, height / 2 - 50, 200, 20, Component.literal("Set Security Password:"), font)
        addRenderableWidget(labelWidget)

        passwordEditBox = EditBox(
            font,
            width / 2 - 100, height / 2 - 20,
            200, 20,
            Component.literal("Password")
        )
        passwordEditBox?.setMaxLength(16)
        addRenderableWidget(passwordEditBox!!)

        val saveButton = Button.builder(Component.literal("Register Keycard")) { button ->
            val password = passwordEditBox?.value?.trim() ?: ""
            if (password.isNotEmpty()) {
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(SetKeycardPasswordPayload(password))
                onClose()
            }
        }.bounds(width / 2 - 100, height / 2 + 15, 200, 20).build()
        addRenderableWidget(saveButton)
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick)
        super.render(guiGraphics, mouseX, mouseY, partialTick)
    }
}
