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
import net.minecraft.client.gui.components.AbstractSliderButton

class CameraStationScreen(menu: CameraStationMenu, playerInv: Inventory, title: Component) : AbstractContainerScreen<CameraStationMenu>(menu, playerInv, title) {

    companion object {
        private val UNASSIGNED_TEXTURE = ResourceLocation.fromNamespaceAndPath(Crtangarine.ID, "textures/gui/ownerless_station.png")
        private val OWNED_TEXTURE = ResourceLocation.fromNamespaceAndPath(Crtangarine.ID, "textures/gui/owned_station.png")

        // --- Font Colors ---
        const val TITLE_COLOR = 0x1A1A1A // Dark Gray / Black (#1A1A1A)
        const val SIDEBAR_TITLE_COLOR = 0xDDDDDD // Bright Gray (#DDDDDD)

        // --- GUI General Canvas Dimensions (rendering size on screen) ---
        const val WIDTH_MAIN = 176
        const val WIDTH_TOTAL = 323
        const val HEIGHT = 164

        // --- Texture File Dimensions (actual size of the PNG file on disk) ---
        const val TEXTURE_WIDTH = 323
        const val TEXTURE_HEIGHT = 164

        // --- Header Title Positions (relative to container origin) ---
        const val TITLE_X = 8
        const val TITLE_Y = 6
        const val SIDEBAR_TITLE_X = 188
        const val SIDEBAR_TITLE_Y = 8

        // --- Rename Input Field (Station Name) ---
        const val NAME_EDIT_X = 13
        const val NAME_EDIT_Y = 19
        const val NAME_EDIT_WIDTH = 150
        const val NAME_EDIT_HEIGHT = 18

        // --- Register Identity Button (Unowned State) ---
        const val REGISTER_BTN_X = 25
        const val REGISTER_BTN_Y = 19
        const val REGISTER_BTN_WIDTH = 63
        const val REGISTER_BTN_HEIGHT = 18

        // --- Surveillance Cameras List Sidebar (Bounds: W=133, H=152 from X=184, Y=6) ---
        const val CAM_MAX_COUNT = 3
        const val CAM_LIST_START_Y = 20 // Starts below "Surveillance Cams" header
        const val CAM_ROW_SPACING = 46 // 42 height + 4 vertical gap

        // --- Camera Button Dimensions (Centered inside 133px width area -> 4px margins) ---
        const val CAM_BTN_X = 188
        const val CAM_BTN_WIDTH = 105
        const val CAM_BTN_HEIGHT = 42

        // --- Icon Column X position (Locate & Delete stacked vertically) ---
        const val ICON_COL_X = 295

        // --- Locate Button (Top, magnifying glass) ---
        const val LOCATE_BTN_X_OFFSET = 2  // centered inside 18px column: 295 + 2 = 297
        const val LOCATE_BTN_Y_OFFSET = 4  // starts at Y = 4
        const val LOCATE_BTN_SIZE = 14    // size 14x14

        // --- Delete Button (Bottom, red X cancel) ---
        const val DELETE_BTN_X_OFFSET = 1  // centered inside 18px column: 295 + 1 = 296
        const val DELETE_BTN_Y_OFFSET = 22 // starts at Y = 22
        const val DELETE_BTN_SIZE = 16    // size 16x16
    }

    private var nameEdit: EditBox? = null

    init {
        // --- GUI Canvas Size ---
        imageWidth = WIDTH_MAIN
        imageHeight = HEIGHT
    }

    override fun init() {
        super.init()

        val isOwned = menu.blockEntity?.ownerUuid?.isNotEmpty() == true

        if (isOwned) {
            // 1. Station Rename Input Field
            val editBox = EditBox(font, leftPos + NAME_EDIT_X, topPos + NAME_EDIT_Y, NAME_EDIT_WIDTH, NAME_EDIT_HEIGHT, Component.literal("Station Name"))
            editBox.value = menu.blockEntity?.customName ?: ""
            editBox.setHint(Component.literal("Station name..."))
            editBox.setResponder { newName ->
                PacketDistributor.sendToServer(me.orange.crtangarine.network.UpdateStationNamePayload(menu.blockPos, newName))
            }
            editBox.active = true
            nameEdit = editBox
            addRenderableWidget(editBox)

            // 2. Camera Action Buttons Sidebar
            val cameras = menu.blockEntity?.linkedCameras ?: emptyList()
            var yOffset = CAM_LIST_START_Y
            for (i in cameras.indices) {
                if (i >= CAM_MAX_COUNT) break
                val camPos = cameras[i]
                
                // Main Aim Button (Identified persistently by coordinates, rendering multi-line)
                 val level = minecraft?.level
                 val cameraBe = if (level != null && level.hasChunk(camPos.x shr 4, camPos.z shr 4)) {
                     level.getBlockEntity(camPos) as? me.orange.crtangarine.block.CameraBlockEntity
                 } else null
                 val isOnline = cameraBe != null && cameraBe.linkedStationPos == menu.blockPos
                val aimBtn = object : Button(
                    leftPos + CAM_BTN_X, topPos + yOffset, CAM_BTN_WIDTH, CAM_BTN_HEIGHT,
                    Component.empty(), { _ ->
                        PacketDistributor.sendToServer(AimCameraPayload(menu.blockPos, camPos))
                    }, { supplier -> supplier.get() }
                ) {
                    override fun renderWidget(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
                        super.renderWidget(guiGraphics, mouseX, mouseY, partialTick)
                        
                        val coordsStr = "${camPos.x}, ${camPos.y}, ${camPos.z}"
                        val statusStr = if (isOnline) "Active" else "Offline"
                        val statusColor = if (isOnline) 0x33FF33 else 0xFF3333 // CRT Green vs CRT Red

                        val fontRenderer = net.minecraft.client.Minecraft.getInstance().font
                        
                        // Line 1: Coordinates (centered horizontally, shifted up)
                        val xCoords = this.x + (this.width - fontRenderer.width(coordsStr)) / 2
                        val yCoords = this.y + 8
                        guiGraphics.drawString(fontRenderer, coordsStr, xCoords, yCoords, 0xFFFFFF, true)

                        // Line 2: Status (centered horizontally, shifted down)
                        val xStatus = this.x + (this.width - fontRenderer.width(statusStr)) / 2
                        val yStatus = this.y + 24
                        guiGraphics.drawString(fontRenderer, statusStr, xStatus, yStatus, statusColor, true)
                    }
                }
                aimBtn.active = isOnline
                addRenderableWidget(aimBtn)

                // Locate Button (Sprite magnifying glass)
                val locateBtn = SpriteButton(
                    leftPos + ICON_COL_X + LOCATE_BTN_X_OFFSET, topPos + yOffset + LOCATE_BTN_Y_OFFSET,
                    LOCATE_BTN_SIZE, LOCATE_BTN_SIZE,
                    ResourceLocation.withDefaultNamespace("icon/search"),
                    ResourceLocation.withDefaultNamespace("icon/search")
                ) { _ ->
                    PacketDistributor.sendToServer(LocateCameraPayload(menu.blockPos, camPos))
                    onClose()
                }
                addRenderableWidget(locateBtn)

                // Delete/Unlink Button (Sprite close X button)
                val deleteBtn = SpriteButton(
                    leftPos + ICON_COL_X + DELETE_BTN_X_OFFSET, topPos + yOffset + DELETE_BTN_Y_OFFSET,
                    DELETE_BTN_SIZE, DELETE_BTN_SIZE,
                    ResourceLocation.withDefaultNamespace("container/beacon/cancel"),
                    ResourceLocation.withDefaultNamespace("container/beacon/cancel")
                ) { _ ->
                    PacketDistributor.sendToServer(me.orange.crtangarine.network.UnlinkCameraPayload(menu.blockPos, camPos))
                    menu.blockEntity?.linkedCameras?.remove(camPos)
                    rebuildWidgets()
                }
                addRenderableWidget(deleteBtn)

                yOffset += CAM_ROW_SPACING
            }
        } else {

            // 3. Burn Identity Button (only shown in unassigned/ownerless state)
            val burnBtn = Button.builder(Component.literal("Register")) { _ ->
                minecraft?.gameMode?.handleInventoryButtonClick(menu.containerId, 0)
                
                // Optimistic client-side UI update to reveal owned state immediately
                val keycard = menu.container.getItem(0)
                if (!keycard.isEmpty && keycard.item is me.orange.crtangarine.item.SecurityKeycardItem) {
                    val customData = keycard.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA) ?: net.minecraft.world.item.component.CustomData.EMPTY
                    val tag = customData.copyTag()
                    val keycardOwner = tag.getString("OwnerUUID")
                    if (keycardOwner.isNotEmpty()) {
                        menu.blockEntity?.ownerUuid = keycardOwner
                        menu.container.setItem(0, net.minecraft.world.item.ItemStack.EMPTY)
                        rebuildWidgets()
                    }
                }
            }.bounds(leftPos + REGISTER_BTN_X, topPos + REGISTER_BTN_Y, REGISTER_BTN_WIDTH, REGISTER_BTN_HEIGHT).build()
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
        val renderWidth = if (isOwned) WIDTH_TOTAL else WIDTH_MAIN
        guiGraphics.blit(texture, leftPos, topPos, 0f, 0f, renderWidth, imageHeight, TEXTURE_WIDTH, TEXTURE_HEIGHT)
    }

    override fun renderLabels(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val isOwned = menu.blockEntity?.ownerUuid?.isNotEmpty() == true
        val titleText = if (isOwned) {
            Component.literal(title.string + " - Registered")
        } else {
            title
        }
        guiGraphics.drawString(font, titleText, TITLE_X, TITLE_Y, TITLE_COLOR, false)

        if (isOwned) {
            // Draw Sidebar Header Title
            guiGraphics.drawString(font, "Cameras", SIDEBAR_TITLE_X, SIDEBAR_TITLE_Y, SIDEBAR_TITLE_COLOR, false)
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

class SpriteButton(
    x: Int, y: Int,
    width: Int, height: Int,
    private val normalSprite: ResourceLocation,
    private val hoveredSprite: ResourceLocation,
    onPress: OnPress
) : Button(x, y, width, height, Component.empty(), onPress, { supplier -> supplier.get() }) {

    override fun renderWidget(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val activeSprite = if (isHoveredOrFocused) hoveredSprite else normalSprite
        guiGraphics.blitSprite(activeSprite, x, y, width, height)
    }
}
