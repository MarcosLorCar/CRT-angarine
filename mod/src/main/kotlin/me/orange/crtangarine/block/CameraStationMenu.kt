package me.orange.crtangarine.block

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import me.orange.crtangarine.item.SecurityKeycardItem
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.component.CustomData

class CameraStationMenu(windowId: Int, playerInv: Inventory, val blockPos: BlockPos) : AbstractContainerMenu(ModBlocks.CAMERA_STATION_MENU_TYPE, windowId) {

    val blockEntity = playerInv.player.level().getBlockEntity(blockPos) as? CameraStationBlockEntity
    val container = blockEntity?.inventory ?: net.minecraft.world.SimpleContainer(1)

    init {
        // Slot for Security Keycard at (80, 18)
        addSlot(object : Slot(container, 0, 80, 18) {
            override fun mayPlace(stack: ItemStack): Boolean {
                return stack.item is SecurityKeycardItem
            }
        })

        // Player Inventory Slots starting at y = 84
        for (row in 0..2) {
            for (col in 0..8) {
                addSlot(Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18))
            }
        }

        // Player Hotbar Slots starting at y = 142
        for (col in 0..8) {
            addSlot(Slot(playerInv, col, 8 + col * 18, 142))
        }
    }

    override fun stillValid(player: Player): Boolean {
        return true
    }

    override fun clickMenuButton(player: Player, id: Int): Boolean {
        if (id == 0) {
            // Burn Identity
            val keycard = container.getItem(0)
            if (!keycard.isEmpty && keycard.item is SecurityKeycardItem) {
                val customData = keycard.get(DataComponents.CUSTOM_DATA) ?: CustomData.EMPTY
                val tag = customData.copyTag()
                val keycardOwner = tag.getString("OwnerUUID")
                if (keycardOwner.isNotEmpty()) {
                    val currentOwner = blockEntity?.ownerUuid ?: ""
                    if (currentOwner.isEmpty() || currentOwner == player.uuid.toString()) {
                        blockEntity?.ownerUuid = keycardOwner
                        blockEntity?.setChanged()
                        blockEntity?.level?.sendBlockUpdated(blockPos, blockEntity.blockState, blockEntity.blockState, 3)
                        CameraStationRegistry.triggerUpdate()
                        return true
                    }
                }
            }
        }
        return false
    }

    override fun quickMoveStack(player: Player, index: Int): ItemStack {
        var itemstack = ItemStack.EMPTY
        val slot = slots[index]
        if (slot != null && slot.hasItem()) {
            val itemstack1 = slot.item
            itemstack = itemstack1.copy()
            if (index == 0) {
                if (!moveItemStackTo(itemstack1, 1, 37, true)) {
                    return ItemStack.EMPTY
                }
                slot.onQuickCraft(itemstack1, itemstack)
            } else {
                if (itemstack1.item is SecurityKeycardItem) {
                    if (!moveItemStackTo(itemstack1, 0, 1, false)) {
                        return ItemStack.EMPTY
                    }
                } else if (index in 1..27) {
                    if (!moveItemStackTo(itemstack1, 28, 37, false)) {
                        return ItemStack.EMPTY
                    }
                } else if (index in 28..36 && !moveItemStackTo(itemstack1, 1, 28, false)) {
                    return ItemStack.EMPTY
                }
            }
            if (itemstack1.isEmpty) {
                slot.setByPlayer(ItemStack.EMPTY)
            } else {
                slot.setChanged()
            }
            if (itemstack1.count == itemstack.count) {
                return ItemStack.EMPTY
            }
            slot.onTake(player, itemstack1)
        }
        return itemstack
    }
}
