package me.orange.crtangarine.client

import me.orange.crtangarine.block.CameraStationMenu
import me.orange.crtangarine.network.AimCameraPayload
import me.orange.crtangarine.network.LocateCameraPayload
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.neoforged.neoforge.network.PacketDistributor

class CameraStationScreen(menu: CameraStationMenu, playerInv: Inventory, title: Component) : AbstractContainerScreen<CameraStationMenu>(menu, playerInv, title) {

    private lateinit var nameEdit: EditBox

    init {
        // Expand width to 286 to fit the camera sidebar on the right
        imageWidth = 286
        imageHeight = 166
    }

    override fun init() {
        super.init()

        // Add Burn Identity button
        val burnBtn = Button.builder(Component.literal("Burn Identity")) { button ->
            minecraft?.gameMode?.handleInventoryButtonClick(menu.containerId, 0)
        }.bounds(leftPos + 8, topPos + 18, 68, 18).build()
        addRenderableWidget(burnBtn)

        // Add Name Edit Box
        nameEdit = EditBox(font, leftPos + 8, topPos + 44, 160, 18, Component.literal("Station Name"))
        nameEdit.value = menu.blockEntity?.customName ?: ""
        nameEdit.setResponder { newName ->
            PacketDistributor.sendToServer(me.orange.crtangarine.network.UpdateStationNamePayload(menu.blockPos, newName))
        }

        // Only enable custom naming if the station has an owner
        val isOwned = menu.blockEntity?.ownerUuid?.isNotEmpty() == true
        nameEdit.active = isOwned

        addRenderableWidget(nameEdit)

        // Add Aim, Locate and Unlink buttons for linked cameras
        val cameras = menu.blockEntity?.linkedCameras ?: emptyList()
        var yOffset = 20
        for (i in cameras.indices) {
            if (i >= 4) break
            val camPos = cameras[i]
            
            // Aim Camera Button
            val aimBtn = Button.builder(Component.literal("Aim")) { button ->
                PacketDistributor.sendToServer(AimCameraPayload(menu.blockPos, camPos))
            }.bounds(leftPos + 186, topPos + yOffset + 14, 52, 16).build()
            addRenderableWidget(aimBtn)

            // Locate Camera Button
            val locateBtn = Button.builder(Component.literal("Loc")) { button ->
                PacketDistributor.sendToServer(LocateCameraPayload(menu.blockPos, camPos))
                onClose()
            }.bounds(leftPos + 240, topPos + yOffset + 14, 26, 16).build()
            addRenderableWidget(locateBtn)

            // Unlink Camera Button (renders as X)
            val unlinkBtn = Button.builder(Component.literal("X")) { button ->
                PacketDistributor.sendToServer(me.orange.crtangarine.network.UnlinkCameraPayload(menu.blockPos, camPos))
            }.bounds(leftPos + 268, topPos + yOffset + 14, 12, 16).build()
            addRenderableWidget(unlinkBtn)

            yOffset += 34
        }
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick)
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        renderTooltip(guiGraphics, mouseX, mouseY)
    }

    override fun renderBg(guiGraphics: GuiGraphics, partialTick: Float, mouseX: Int, mouseY: Int) {
        // Draw main vanilla panel
        guiGraphics.fill(leftPos, topPos, leftPos + 176, topPos + imageHeight, 0xFFC6C6C6.toInt())
        drawPanelBorders(guiGraphics, leftPos, topPos, 176, imageHeight)

        // Draw sidebar panel for cameras list
        guiGraphics.fill(leftPos + 180, topPos, leftPos + 286, topPos + imageHeight, 0xFFC6C6C6.toInt())
        drawPanelBorders(guiGraphics, leftPos + 180, topPos, 106, imageHeight)

        // Draw keycard slot outline at (80, 18)
        drawSlotBg(guiGraphics, leftPos + 80, topPos + 18)

        // Draw Player Inventory slot backgrounds
        for (row in 0..2) {
            for (col in 0..8) {
                drawSlotBg(guiGraphics, leftPos + 8 + col * 18, topPos + 84 + row * 18)
            }
        }

        // Draw Player Hotbar slot backgrounds
        for (col in 0..8) {
            drawSlotBg(guiGraphics, leftPos + 8 + col * 18, topPos + 142)
        }
    }

    override fun renderLabels(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        // Draw titles relative to leftPos and topPos
        guiGraphics.drawString(font, title, 8, 6, 0x404040, false)

        val ownerUuid = menu.blockEntity?.ownerUuid ?: ""
        val statusText = if (ownerUuid.isEmpty()) "Unassigned" else "Owner Bound"
        guiGraphics.drawString(font, statusText, 100, 6, 0x404040, false)

        guiGraphics.drawString(font, playerInventoryTitle, 8, 73, 0x404040, false)

        // Draw sidebar title and camera labels
        guiGraphics.drawString(font, "Surveillance Cams", 186, 6, 0x404040, false)

        val cameras = menu.blockEntity?.linkedCameras ?: emptyList()
        var textYOffset = 20
        for (i in cameras.indices) {
            if (i >= 4) break
            val camPos = cameras[i]
            guiGraphics.drawString(font, "Cam #${i + 1}: ${camPos.x}, ${camPos.y}, ${camPos.z}", 186, textYOffset, 0x404040, false)
            textYOffset += 34
        }
    }

    private fun drawSlotBg(guiGraphics: GuiGraphics, x: Int, y: Int) {
        guiGraphics.fill(x, y, x + 18, y + 18, 0xFF8B8B8B.toInt())
        guiGraphics.fill(x + 1, y + 17, x + 18, y + 18, 0xFFFFFFFF.toInt())
        guiGraphics.fill(x + 17, y + 1, x + 18, y + 18, 0xFFFFFFFF.toInt())
        guiGraphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF373737.toInt())
    }

    private fun drawPanelBorders(guiGraphics: GuiGraphics, x: Int, y: Int, w: Int, h: Int) {
        guiGraphics.fill(x, y, x + w, y + 1, 0xFFFFFFFF.toInt())
        guiGraphics.fill(x, y, x + 1, y + h, 0xFFFFFFFF.toInt())
        guiGraphics.fill(x, y + h - 1, x + w, y + h, 0xFF555555.toInt())
        guiGraphics.fill(x + w - 1, y, x + w, y + h, 0xFF555555.toInt())
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (nameEdit.isFocused) {
            if (keyCode == 256) { // Escape key should still close the screen
                this.onClose()
                return true
            }
            if (nameEdit.keyPressed(keyCode, scanCode, modifiers)) {
                return true
            }
            return true // consume keypress to prevent closing inventory (typing 'e')
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }
}
