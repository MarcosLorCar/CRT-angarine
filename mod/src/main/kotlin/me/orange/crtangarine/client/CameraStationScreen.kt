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
import net.minecraft.resources.ResourceLocation
import me.orange.crtangarine.Crtangarine

class CameraStationScreen(menu: CameraStationMenu, playerInv: Inventory, title: Component) : AbstractContainerScreen<CameraStationMenu>(menu, playerInv, title) {

    companion object {
        private val UNASSIGNED_TEXTURE = ResourceLocation.fromNamespaceAndPath(Crtangarine.ID, "textures/gui/ownerless_station.png")
        private val OWNED_TEXTURE = ResourceLocation.fromNamespaceAndPath(Crtangarine.ID, "textures/gui/owned_station.png")
    }

    private var nameEdit: EditBox? = null

    init {
        val isOwned = menu.blockEntity?.ownerUuid?.isNotEmpty() == true
        
        // --- GUI Canvas Size ---
        imageWidth = if (isOwned) 286 else 176
        imageHeight = 133
    }

    override fun init() {
        super.init()

        val isOwned = menu.blockEntity?.ownerUuid?.isNotEmpty() == true

        if (isOwned) {
            // 1. Station Rename Input Field
            // Customize bounds: (x, y, width, height) relative to leftPos and topPos
            val editBox = EditBox(font, leftPos + 8, topPos + 44, 160, 18, Component.literal("Station Name"))
            editBox.value = menu.blockEntity?.customName ?: ""
            editBox.setResponder { newName ->
                PacketDistributor.sendToServer(me.orange.crtangarine.network.UpdateStationNamePayload(menu.blockPos, newName))
            }
            editBox.active = true
            nameEdit = editBox
            addRenderableWidget(editBox)

            // 2. Camera Action Buttons Sidebar
            val cameras = menu.blockEntity?.linkedCameras ?: emptyList()
            var yOffset = 20
            for (i in cameras.indices) {
                if (i >= 4) break
                val camPos = cameras[i]
                
                // Aim Camera Button
                // Customize bounds: (x, y, width, height) relative to leftPos and topPos
                val aimBtn = Button.builder(Component.literal("Aim")) { button ->
                    PacketDistributor.sendToServer(AimCameraPayload(menu.blockPos, camPos))
                }.bounds(leftPos + 186, topPos + yOffset + 14, 52, 16).build()
                addRenderableWidget(aimBtn)

                // Locate Camera Button (plays world sound and draws coordinates tracer particles)
                val locateBtn = Button.builder(Component.literal("Loc")) { button ->
                    PacketDistributor.sendToServer(LocateCameraPayload(menu.blockPos, camPos))
                    onClose()
                }.bounds(leftPos + 240, topPos + yOffset + 14, 26, 16).build()
                addRenderableWidget(locateBtn)

                // Unlink Camera Button (Removes camera link from station)
                val unlinkBtn = Button.builder(Component.literal("X")) { button ->
                    PacketDistributor.sendToServer(me.orange.crtangarine.network.UnlinkCameraPayload(menu.blockPos, camPos))
                }.bounds(leftPos + 268, topPos + yOffset + 14, 12, 16).build()
                addRenderableWidget(unlinkBtn)

                yOffset += 34
            }
        } else {
            // 3. Burn Identity Button (only shown in unassigned/ownerless state)
            // Customize bounds: (x, y, width, height) relative to leftPos and topPos
            val burnBtn = Button.builder(Component.literal("Register")) { _ ->
                minecraft?.gameMode?.handleInventoryButtonClick(menu.containerId, 0)
            }.bounds(leftPos + 25, topPos + 19, 63, 18).build()
            addRenderableWidget(burnBtn)
        }
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick)
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        renderTooltip(guiGraphics, mouseX, mouseY)
    }

    override fun renderBg(guiGraphics: GuiGraphics, partialTick: Float, mouseX: Int, mouseY: Int) {
        val isOwned = menu.blockEntity?.ownerUuid?.isNotEmpty() == true
        val texture = if (isOwned) OWNED_TEXTURE else UNASSIGNED_TEXTURE
        guiGraphics.blit(texture, leftPos, topPos, 0f, 0f, imageWidth, imageHeight, imageWidth, imageHeight)
    }

    override fun renderLabels(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        guiGraphics.drawString(font, title, 8, 6, 0x404040, false)

        val isOwned = menu.blockEntity?.ownerUuid?.isNotEmpty() == true

        if (isOwned) {
            // Sidebar titles & camera text
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
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (nameEdit != null && nameEdit!!.isFocused) {
            if (keyCode == 256) { // Escape key closes the screen
                this.onClose()
                return true
            }
            if (nameEdit!!.keyPressed(keyCode, scanCode, modifiers)) {
                return true
            }
            return true // Consume keypress to prevent closing screen on typing inventory key
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }
}
